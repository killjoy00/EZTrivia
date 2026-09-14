#!/usr/bin/env python3
import json
import os
from pathlib import Path

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "com.rsm.eztrivia"
LANGUAGE = "en-US"
IMAGE_TYPE = "phoneScreenshots"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_BASE = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"


def require_ok(response, context, statuses=(200,)):
    if response.status_code not in statuses:
        print(f"::error::{context}: HTTP {response.status_code}: {response.text[:4000]}")
        raise SystemExit(1)
    return response


def main():
    raw = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise SystemExit("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not available")

    screenshots = sorted(Path("play-store-screenshots").glob("*.png"))
    if len(screenshots) < 2:
        raise SystemExit(f"Expected at least 2 phone screenshots, found {len(screenshots)}")

    creds = service_account.Credentials.from_service_account_info(
        json.loads(raw),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(creds)

    edit = require_ok(
        session.post(f"{BASE}/applications/{PACKAGE}/edits", json={}, timeout=60),
        "Could not create Play edit",
    ).json()
    edit_id = edit["id"]

    require_ok(
        session.delete(
            f"{BASE}/applications/{PACKAGE}/edits/{edit_id}/listings/{LANGUAGE}/{IMAGE_TYPE}",
            timeout=60,
        ),
        "Could not clear existing phone screenshots",
    )

    uploaded = []
    for path in screenshots:
        response = require_ok(
            session.post(
                f"{UPLOAD_BASE}/applications/{PACKAGE}/edits/{edit_id}/listings/{LANGUAGE}/{IMAGE_TYPE}",
                params={"uploadType": "media"},
                headers={"Content-Type": "image/png"},
                data=path.read_bytes(),
                timeout=120,
            ),
            f"Could not upload {path.name}",
        )
        image = response.json().get("image", {})
        uploaded.append({"file": path.name, "id": image.get("id"), "sha256": image.get("sha256")})
        print(f"Uploaded {path.name}: {image.get('id')}")

    require_ok(
        session.post(
            f"{BASE}/applications/{PACKAGE}/edits/{edit_id}:commit",
            json={},
            timeout=60,
        ),
        "Could not commit screenshot edit",
    )

    print(f"Committed {len(uploaded)} phone screenshots to the en-US Play listing.")
    print(json.dumps(uploaded, indent=2))


if __name__ == "__main__":
    main()
