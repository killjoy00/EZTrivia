#!/usr/bin/env python3
"""Publish verified Ultttt privacy and support links to its existing Apple draft.

Only changes URL fields on the exact 6835609349 iOS app and its en-US
localizations. It never completes the Apple App Privacy nutrition-label
questionnaire, submits a build or changes a price.
"""
from __future__ import annotations
import base64
import json
import os
import re
import time
from pathlib import Path

import jwt
import requests

APP_ID = "6835609349"
BUNDLE = "com.killjoy00.ultttt"
NAME = "Ultimate Tic/Tac/Toe"
ROOT = "https://killjoy00.github.io/Ultttt"
POLICY = ROOT + "/privacy/"
CHOICES = ROOT + "/privacy-choices/"
SUPPORT = ROOT + "/support/"
HOMEPAGE = ROOT + "/"
BASE = "https://api.appstoreconnect.apple.com/v1"

# The public site must actually serve the new pages before App Store
# metadata is changed. GitHub Pages may take a short while to deploy.
for url, marker in [
    (POLICY, "Your games stay local"),
    (CHOICES, "Manage advertising choices"),
    (SUPPORT, "Frequently asked questions"),
]:
    for attempt in range(8):
        try:
            page = requests.get(url, timeout=18, allow_redirects=True)
            if (page.status_code == 200 and marker in page.text
                    and "text/html" in page.headers.get("Content-Type", "")):
                print("Confirmed public HTML page:", url, flush=True)
                break
            print(f"Page not ready ({page.status_code}) {url}", flush=True)
        except requests.RequestException as exc:
            print(f"Website verification retry {attempt+1}: {type(exc).__name__}", flush=True)
        if attempt == 7:
            raise SystemExit("Public privacy/support page is unavailable; Apple was not modified")
        time.sleep(9)

raw = os.environ["ASC_KEY_P8"].replace("\r", "").strip()
if "BEGIN PRIVATE KEY" not in raw:
    if "\\n" in raw:
        raw = raw.replace("\\n", "\n")
    else:
        try:
            decoded = base64.b64decode(raw).decode()
            if "BEGIN PRIVATE KEY" in decoded:
                raw = decoded
        except (ValueError, UnicodeDecodeError):
            pass
if "BEGIN PRIVATE KEY" not in raw:
    compact = "".join(raw.split())
    raw = ("-----BEGIN PRIVATE KEY-----\n"
           + "\n".join(compact[i:i+64] for i in range(0, len(compact), 64))
           + "\n-----END PRIVATE KEY-----\n")
now = int(time.time())
token = jwt.encode(
    {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1200,
     "aud": "appstoreconnect-v1"},
    raw, algorithm="ES256", headers={"kid": os.environ["ASC_KEY_ID"]}
)
session = requests.Session()
session.headers.update({"Authorization": "Bearer " + token,
                        "Content-Type": "application/json", "Accept": "application/json"})

def apple(method, path, payload=None, params=None, optional=False):
    try:
        response = session.request(method, BASE + path, json=payload,
                                   params=params, timeout=50)
    except requests.RequestException as exc:
        raise RuntimeError("Apple request network error: " + str(type(exc).__name__)) from exc
    if not 200 <= response.status_code < 300:
        if optional and response.status_code in (403, 404):
            print("Apple read-only optional endpoint unavailable:", path,
                  "HTTP", response.status_code)
            return None
        try:
            detail = "; ".join(f"{x.get('code')}: {x.get('detail')}"
                               for x in response.json().get("errors", [])[:2])
        except (ValueError, TypeError):
            detail = ""
        raise RuntimeError(f"Apple {method} {path} HTTP {response.status_code}: {detail[:350]}")
    return response.json() if response.content else {}

def rows(path, params=None):
    found = []
    while path:
        page = apple("GET", path, params=params)
        found.extend(page.get("data", []))
        path = page.get("links", {}).get("next")
        if path and path.startswith("https://api.appstoreconnect.apple.com/v1"):
            path = path.replace("https://api.appstoreconnect.apple.com/v1", "", 1)
        params = None
    return found

def patch_existing(path, kind, record, expected):
    before = record.get("attributes", {})
    changed = {key: val for key, val in expected.items() if before.get(key) != val}
    if changed:
        apple("PATCH", path + "/" + record["id"], {
            "data": {"type": kind, "id": record["id"], "attributes": changed}
        })
        print("Updated", kind, ",".join(changed.keys()))
    else:
        print("Already correct:", kind)
    after = apple("GET", path + "/" + record["id"])["data"]
    differences = {key: (value, after.get("attributes", {}).get(key))
                   for key, value in expected.items()
                   if after.get("attributes", {}).get(key) != value}
    if differences:
        raise RuntimeError("Apple metadata readback mismatch: " + repr(differences))
    print("Verified Apple", kind, expected)

apps = rows("/apps", {"filter[bundleId]": BUNDLE, "limit": 5})
if len(apps) != 1 or apps[0]["id"] != APP_ID or apps[0]["attributes"].get("name") != NAME:
    raise SystemExit("Apple app ID, bundle ID or name does not match; refusing all updates")
print("Verified exact Apple app", NAME, APP_ID, BUNDLE)

infos = rows(f"/apps/{APP_ID}/appInfos", {"limit": 30})
if not infos:
    raise RuntimeError("No Apple app info record")
info = infos[0]
locs = rows(f"/appInfos/{info['id']}/appInfoLocalizations", {"limit": 30})
loc = next((x for x in locs if x["attributes"].get("locale") == "en-US"), None)
if not loc:
    raise RuntimeError("Missing English app-level localization")
patch_existing(
    "/appInfoLocalizations", "appInfoLocalizations", loc,
    {"privacyPolicyUrl": POLICY, "privacyChoicesUrl": CHOICES}
)

versions = rows(f"/apps/{APP_ID}/appStoreVersions", {"limit": 30})
version = next((x for x in versions
                if x["attributes"].get("versionString") == "1.0"
                and x["attributes"].get("platform") == "IOS"
                and x["attributes"].get("appStoreState") == "PREPARE_FOR_SUBMISSION"), None)
if version is None:
    raise RuntimeError("No editable Apple iOS 1.0 version; cannot change support link")
vl = rows(f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations", {"limit": 40})
en = next((x for x in vl if x["attributes"].get("locale") == "en-US"), None)
if en is None:
    raise RuntimeError("Missing en-US iOS version localization")
patch_existing(
    "/appStoreVersionLocalizations", "appStoreVersionLocalizations", en,
    {"supportUrl": SUPPORT, "marketingUrl": HOMEPAGE}
)

betas = rows(f"/apps/{APP_ID}/betaAppLocalizations", {"limit": 40})
beta = next((x for x in betas if x["attributes"].get("locale") == "en-US"), None)
if beta is not None:
    patch_existing(
        "/betaAppLocalizations", "betaAppLocalizations", beta,
        {"privacyPolicyUrl": POLICY, "marketingUrl": HOMEPAGE}
    )
else:
    print("No existing TestFlight beta app localization; not creating unrelated metadata")

print("SUCCESS: Privacy Policy URL, Privacy Choices URL, Support URL and")
print("developer marketing URL saved and verified for the existing iOS 1.0 draft.")
print("Only public-facing links were changed; no App Privacy labels, ads,")
print("App Review submission, IAP prices or live binaries were modified.")
