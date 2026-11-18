#!/usr/bin/env python3
import json
import os
from pathlib import Path
from urllib.parse import quote

import requests
from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "com.rsm.eztrivia"
PGS_APP_ID = "406580555223"
PRODUCT_ID = "com.rsm.eztrivia.removeads"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3"


def request_json(session, method, url, *, ok=(200,), **kwargs):
    response = session.request(method, url, timeout=60, **kwargs)
    if response.status_code not in ok:
        return {
            "ok": False,
            "status": response.status_code,
            "body": response.text[:4000],
        }
    if not response.content:
        return {"ok": True, "status": response.status_code, "body": None}
    try:
        body = response.json()
    except ValueError:
        body = response.text[:4000]
    return {"ok": True, "status": response.status_code, "body": body}


def main():
    raw = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise SystemExit("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not available")

    info = json.loads(raw)
    creds = service_account.Credentials.from_service_account_info(
        info,
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(creds)

    report = {
        "package": PACKAGE,
        "playGamesApplicationId": PGS_APP_ID,
        "productId": PRODUCT_ID,
    }

    edit = request_json(
        session,
        "POST",
        f"{BASE}/applications/{PACKAGE}/edits",
        ok=(200,),
        json={},
    )
    report["editCreate"] = edit
    edit_id = None
    if edit["ok"] and isinstance(edit["body"], dict):
        edit_id = edit["body"].get("id")

    version_codes = []
    if edit_id:
        track = request_json(
            session,
            "GET",
            f"{BASE}/applications/{PACKAGE}/edits/{edit_id}/tracks/internal",
        )
        report["internalTrack"] = track
        if track["ok"] and isinstance(track["body"], dict):
            for release in track["body"].get("releases", []):
                for code in release.get("versionCodes", []):
                    try:
                        version_codes.append(int(code))
                    except (TypeError, ValueError):
                        pass

        report["listings"] = request_json(
            session,
            "GET",
            f"{BASE}/applications/{PACKAGE}/edits/{edit_id}/listings",
        )
        report["details"] = request_json(
            session,
            "GET",
            f"{BASE}/applications/{PACKAGE}/edits/{edit_id}/details",
        )
        for image_type in ("phoneScreenshots", "featureGraphic", "icon"):
            report[f"images_{image_type}"] = request_json(
                session,
                "GET",
                f"{BASE}/applications/{PACKAGE}/edits/{edit_id}/listings/en-US/{image_type}",
            )

    report["oneTimeProduct"] = request_json(
        session,
        "GET",
        f"{BASE}/applications/{PACKAGE}/oneTimeProducts/{quote(PRODUCT_ID, safe='')}",
        ok=(200, 404),
    )
    report["legacyInAppProduct"] = request_json(
        session,
        "GET",
        f"{BASE}/applications/{PACKAGE}/inappproducts/{quote(PRODUCT_ID, safe='')}",
        ok=(200, 404),
    )

    latest_version = max(version_codes) if version_codes else None
    report["latestInternalVersionCode"] = latest_version
    if latest_version is not None:
        generated = request_json(
            session,
            "GET",
            f"{BASE}/applications/{PACKAGE}/generatedApks/{latest_version}",
        )
        report["generatedApks"] = generated
        hashes = []
        if generated["ok"] and isinstance(generated["body"], dict):
            for group in generated["body"].get("generatedApks", []):
                value = group.get("certificateSha256Hash")
                if value:
                    hashes.append(value)
        report["appSigningCertificateSha256Hashes"] = hashes

    # This read may require player/user OAuth rather than a service account. It is
    # deliberately best-effort; when it works, enabledFeatures tells us whether
    # SNAPSHOTS (Saved Games) is already enabled.
    try:
        games_creds = service_account.Credentials.from_service_account_info(
            info,
            scopes=["https://www.googleapis.com/auth/games"],
        )
        games_session = AuthorizedSession(games_creds)
        report["playGamesApplication"] = request_json(
            games_session,
            "GET",
            f"https://games.googleapis.com/games/v1/applications/{PGS_APP_ID}?platformType=ANDROID&language=en-US",
            ok=(200, 401, 403, 404),
        )
    except Exception as exc:
        report["playGamesApplication"] = {"ok": False, "exception": str(exc)}

    Path("play-console-audit.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    print("=== EZ Trivia Play Console audit ===")
    print(f"Latest internal version: {latest_version}")
    print("App-signing SHA-256 hashes:", report.get("appSigningCertificateSha256Hashes", []))
    otp = report["oneTimeProduct"]
    print("One-time product status:", otp.get("status"), "ok=" + str(otp.get("ok")))
    pgs = report["playGamesApplication"]
    if pgs.get("ok") and isinstance(pgs.get("body"), dict):
        print("PGS enabled features:", pgs["body"].get("enabledFeatures", []))
    else:
        print("PGS application read status:", pgs.get("status"), pgs.get("exception", ""))


if __name__ == "__main__":
    main()
