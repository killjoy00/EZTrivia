#!/usr/bin/env python3
"""Reproducible manual Apple Distribution signing for Stuffy Land.

The RSA certificate private key is AES-256-GCM encrypted using an HKDF key
derived from the existing App Store Connect API private key. The encrypted
envelope is committed to GitHub; the unencrypted RSA key NEVER leaves a trusted
GitHub Actions runner. Keep ASC_KEY_P8 in repository secrets. Rotating that key
requires re-encrypting the envelope before the old secret is discarded.

This script never revokes certificates or provisioning profiles. A certificate
is created only if no existing one matches the persistent RSA public key and
the team has no other modern Apple Distribution certificate.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import jwt
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.serialization import pkcs12

BUNDLE_ID = "pro.stuffyland.stories"
PROFILE_NAME = "StuffyLand CI App Store Distribution"
AAD = b"killjoy00/EZTrivia|StuffyLand|apple-distribution-rsa|v1"
KDF_INFO = b"StuffyLand/Apple Distribution signing identity v1"
API = "https://api.appstoreconnect.apple.com"
ALLOWED_CERT_TYPES = {"DISTRIBUTION", "IOS_DISTRIBUTION"}


def api_private_key():
    raw = os.environ["ASC_KEY_P8"].strip().replace("\r", "")
    if "BEGIN " in raw:
        raw = raw.replace("\\n", "\n")
    else:
        try:
            decoded = base64.b64decode(raw, validate=True).decode()
        except Exception:
            decoded = ""
        if "BEGIN " in decoded:
            raw = decoded
        else:
            compact = "".join(raw.split())
            raw = "-----BEGIN PRIVATE KEY-----\n" + "\n".join(
                compact[i:i + 64] for i in range(0, len(compact), 64)
            ) + "\n-----END PRIVATE KEY-----\n"
    key = serialization.load_pem_private_key(raw.encode(), password=None)
    canonical = key.private_bytes(
        serialization.Encoding.DER,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    return key, canonical


def derive_key(asc_key_der: bytes, salt: bytes) -> bytes:
    return HKDF(algorithm=hashes.SHA256(), length=32, salt=salt,
                info=KDF_INFO).derive(asc_key_der)


def public_key_sha(private) -> str:
    encoded = private.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo)
    return hashlib.sha256(encoded).hexdigest()


def bootstrap_key(path: Path, asc_key_der: bytes) -> None:
    if path.exists():
        key = load_encrypted_key(path, asc_key_der)
        assert isinstance(key, rsa.RSAPrivateKey)
        print("Existing encrypted signing key is readable; fingerprint:",
              public_key_sha(key)[:16])
        return
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    raw = private.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption())
    salt = secrets.token_bytes(16)
    nonce = secrets.token_bytes(12)
    encrypted = AESGCM(derive_key(asc_key_der, salt)).encrypt(nonce, raw, AAD)
    envelope = {
        "format": "stuffyland-apple-distribution-key-v1",
        "algorithm": "AES-256-GCM",
        "kdf": "HKDF-SHA256",
        "salt": base64.b64encode(salt).decode(),
        "nonce": base64.b64encode(nonce).decode(),
        "ciphertext": base64.b64encode(encrypted).decode(),
        "publicKeySHA256": public_key_sha(private),
        "securityNote": "Encrypted; decryption requires private ASC_KEY_P8 secret."
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(envelope, indent=2) + "\n")
    print("Encrypted key created; fingerprint:", public_key_sha(private)[:16])
    print("No signing private key was written to the repository in plaintext.")


def load_encrypted_key(path: Path, asc_key_der: bytes):
    envelope = json.loads(path.read_text())
    assert envelope.get("format") == "stuffyland-apple-distribution-key-v1"
    assert envelope.get("algorithm") == "AES-256-GCM"
    salt = base64.b64decode(envelope["salt"], validate=True)
    nonce = base64.b64decode(envelope["nonce"], validate=True)
    ciphertext = base64.b64decode(envelope["ciphertext"], validate=True)
    data = AESGCM(derive_key(asc_key_der, salt)).decrypt(nonce, ciphertext, AAD)
    private = serialization.load_pem_private_key(data, password=None)
    assert isinstance(private, rsa.RSAPrivateKey)
    assert public_key_sha(private) == envelope["publicKeySHA256"]
    return private


def token_for(api_key, kid: str, issuer: str) -> str:
    now = int(time.time())
    return jwt.encode(
        {"iss": issuer, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"},
        api_key, algorithm="ES256", headers={"kid": kid})


def request(token: str, path: str, payload: dict | None = None) -> dict:
    body = json.dumps(payload).encode() if payload is not None else None
    url = path if path.startswith("https://") else API + path
    headers = {
        "Authorization": "Bearer " + token,
        "Accept": "application/json",
    }
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, headers=headers, data=body,
                                 method="POST" if body is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            return json.load(response)
    except urllib.error.HTTPError as err:
        # Never log the API token, certificate private key, or response body
        # (which can contain technical account metadata).
        raise RuntimeError(
            f"App Store Connect {req.method} {urllib.parse.urlsplit(url).path} "
            f"failed with HTTP {err.code}. No certificates were revoked."
        ) from None


def collect(token: str, path: str) -> list[dict]:
    entries = []
    next_page = API + path
    while next_page:
        response = request(token, next_page)
        entries.extend(response.get("data", []))
        next_page = response.get("links", {}).get("next")
    return entries


def cert_object(item: dict):
    data = item.get("attributes", {}).get("certificateContent")
    if not data:
        return None
    binary = base64.b64decode(data)
    try:
        return x509.load_der_x509_certificate(binary)
    except ValueError:
        return x509.load_pem_x509_certificate(binary)


def ensure_certificate(token: str, private) -> tuple[str, x509.Certificate]:
    certs = collect(token, "/v1/certificates?limit=200")
    target_public = private.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo)
    modern_existing = []
    for entry in certs:
        a = entry.get("attributes", {})
        if a.get("certificateType") not in ALLOWED_CERT_TYPES:
            continue
        if a.get("certificateType") == "DISTRIBUTION":
            modern_existing.append(entry)
        public = cert_object(entry)
        if public is None:
            detail = request(token, "/v1/certificates/" + entry["id"])
            public = cert_object(detail["data"])
        if public is None:
            continue
        current_public = public.public_key().public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo)
        if current_public == target_public and public.not_valid_after_utc > dt.datetime.now(dt.timezone.utc):
            print("Reusing the existing persistent Apple Distribution certificate.")
            return entry["id"], public

    if modern_existing:
        raise RuntimeError(
            "Another Apple Distribution certificate exists but its private key "
            "is unavailable to this workflow. No certs will be revoked. "
            "The account holder must supply an existing identity or review the quota."
        )

    csr = x509.CertificateSigningRequestBuilder().subject_name(x509.Name([
        x509.NameAttribute(x509.NameOID.COMMON_NAME, "StuffyLand CI Distribution")
    ])).sign(private, hashes.SHA256())
    csr_text = csr.public_bytes(serialization.Encoding.PEM).decode()
    result = request(token, "/v1/certificates", {
        "data": {"type": "certificates", "attributes": {
            "certificateType": "DISTRIBUTION",
            "csrContent": csr_text
        }}
    })
    created = result["data"]
    certificate = cert_object(created)
    if certificate is None:
        fetched = request(token, "/v1/certificates/" + created["id"])
        certificate = cert_object(fetched["data"])
    if certificate is None:
        raise RuntimeError("Apple returned no certificate content.")
    print("Created one new Apple Distribution certificate; did not revoke any others.")
    return created["id"], certificate


def ensure_profile(token: str, certificate_id: str):
    params = urllib.parse.urlencode({
        "filter[identifier]": BUNDLE_ID,
        "limit": "20"
    })
    bundles = collect(token, "/v1/bundleIds?" + params)
    correct = [x for x in bundles if x.get("attributes", {}).get("identifier") == BUNDLE_ID]
    if len(correct) != 1:
        raise RuntimeError("Expected exactly one registered Stuffy Land Bundle ID.")
    bundle_id = correct[0]["id"]

    existing = collect(token, "/v1/profiles?limit=200&include=certificates,bundleId")
    for profile in existing:
        a = profile.get("attributes", {})
        if a.get("name") != PROFILE_NAME or a.get("profileType") != "IOS_APP_STORE":
            continue
        if a.get("profileState") != "ACTIVE":
            continue
        relations = profile.get("relationships", {})
        matching_certs = {x["id"] for x in
                          (relations.get("certificates", {}).get("data") or [])}
        target_bundle = (relations.get("bundleId", {}).get("data") or {}).get("id")
        if matching_certs == {certificate_id} and target_bundle == bundle_id:
            data = a.get("profileContent")
            if not data:
                detail = request(token, "/v1/profiles/" + profile["id"])
                data = detail["data"]["attributes"].get("profileContent")
            if not data:
                raise RuntimeError("Matched profile lacks profileContent.")
            print("Reusing existing Stuffy Land App Store provisioning profile.")
            return a["uuid"], a["name"], base64.b64decode(data)

    for profile in existing:
        if profile.get("attributes", {}).get("name") == PROFILE_NAME:
            raise RuntimeError(
                "A profile with the expected Stuffy Land signing name already "
                "exists but is not an active match. No profiles were revoked."
            )

    created = request(token, "/v1/profiles", {
        "data": {
            "type": "profiles",
            "attributes": {"name": PROFILE_NAME, "profileType": "IOS_APP_STORE"},
            "relationships": {
                "bundleId": {"data": {"type": "bundleIds", "id": bundle_id}},
                "certificates": {"data": [{"type": "certificates", "id": certificate_id}]}
            }
        }
    })["data"]
    a = created.get("attributes", {})
    raw = a.get("profileContent")
    if not raw:
        detail = request(token, "/v1/profiles/" + created["id"])
        a = detail["data"]["attributes"]
        raw = a["profileContent"]
    print("Created Stuffy Land App Store profile tied to the persistent certificate.")
    return a["uuid"], a["name"], base64.b64decode(raw)


def prepare(path: Path, out: Path, api_key, asc_key_der: bytes) -> None:
    private = load_encrypted_key(path, asc_key_der)
    token = token_for(api_key, os.environ["ASC_KEY_ID"], os.environ["ASC_ISSUER_ID"])
    cert_id, cert = ensure_certificate(token, private)
    uuid, profile_name, profile = ensure_profile(token, cert_id)
    out.mkdir(parents=True, exist_ok=True)
    password = secrets.token_urlsafe(36)
    p12_data = pkcs12.serialize_key_and_certificates(
        name=b"StuffyLand Distribution",
        key=private, cert=cert, cas=None,
        encryption_algorithm=serialization.BestAvailableEncryption(password.encode()))
    p12_path = out / "stuffyland-distribution.p12"
    profile_path = out / (uuid + ".mobileprovision")
    p12_path.write_bytes(p12_data)
    profile_path.write_bytes(profile)
    p12_path.chmod(0o600)
    profile_path.chmod(0o600)
    print("::add-mask::" + password)
    github_env = os.environ.get("GITHUB_ENV")
    if github_env:
        with open(github_env, "a", encoding="utf8") as f:
            f.write("STUFFY_PROFILE_NAME=" + profile_name + "\n")
            f.write("STUFFY_PROFILE_UUID=" + uuid + "\n")
            f.write("STUFFY_P12_PASSWORD=" + password + "\n")
    print("Signing identity and App Store profile prepared locally; no private "
          "signing material was printed or committed.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["bootstrap", "prepare"])
    parser.add_argument("--encrypted-key", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()
    api_key, asc_key_der = api_private_key()
    if args.action == "bootstrap":
        bootstrap_key(args.encrypted_key, asc_key_der)
    else:
        if args.output_dir is None:
            parser.error("--output-dir is required with prepare")
        prepare(args.encrypted_key, args.output_dir, api_key, asc_key_der)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("::error::Stuffy Land manual signing setup failed:", str(error))
        sys.exit(1)
