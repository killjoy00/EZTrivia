#!/usr/bin/env python3
"""Sync EZ Trivia's non-secret Google Play listing basics.

This intentionally touches only developer contact details and the en-US store
icon. Listing copy, screenshots, and feature graphic are managed separately.
"""

from __future__ import annotations

import os
from pathlib import Path

import requests

PACKAGE = "com.rsm.eztrivia"
LANGUAGE = "en-US"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_BASE = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"
ICON = Path("play-store-assets/icon.png")
EXPECTED_TITLE = "EZ Trivia"
EXPECTED_SHORT = "2,341 questions, Daily Challenge, Quick Play, and head-to-head trivia."
CONTACT_WEBSITE = "https://killjoy00.github.io/EZTrivia/support.html"
CONTACT_EMAIL = "killjoy00@yahoo.com"


def require(response: requests.Response, context: str, allowed=(200,)) -> requests.Response:
    if response.status_code not in allowed:
        print(f"::error::{context}: HTTP {response.status_code}: {response.text[:4000]}")
        raise SystemExit(1)
    return response


def main() -> None:
    token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "").strip()
    if not token:
        raise SystemExit("GOOGLE_PLAY_ACCESS_TOKEN is not available")
    if not ICON.is_file():
        raise SystemExit(f"Missing generated Play icon: {ICON}")

    session = requests.Session()
    session.headers.update({"Authorization": f"Bearer {token}"})
    app_base = f"{BASE}/applications/{PACKAGE}"
    edit_id: str | None = None
    committed = False

    try:
        edit = require(
            session.post(f"{app_base}/edits", json={}, timeout=60),
            "Could not create Play edit",
        ).json()
        edit_id = edit["id"]

        listing = require(
            session.get(
                f"{app_base}/edits/{edit_id}/listings/{LANGUAGE}",
                timeout=60,
            ),
            "Could not read en-US Play listing",
        ).json()
        if listing.get("title") != EXPECTED_TITLE:
            raise SystemExit(
                f"Refusing to update store basics: live title is {listing.get('title')!r}, "
                f"expected {EXPECTED_TITLE!r}"
            )
        if listing.get("shortDescription") != EXPECTED_SHORT:
            raise SystemExit(
                "Refusing to update store basics: live short description does not match "
                "android/PLAY_STORE.md"
            )

        current_details = require(
            session.get(f"{app_base}/edits/{edit_id}/details", timeout=60),
            "Could not read Play app details",
        ).json()
        details = {
            "defaultLanguage": current_details.get("defaultLanguage") or LANGUAGE,
            "contactWebsite": CONTACT_WEBSITE,
            "contactEmail": CONTACT_EMAIL,
        }
        if current_details.get("contactPhone"):
            details["contactPhone"] = current_details["contactPhone"]

        require(
            session.put(
                f"{app_base}/edits/{edit_id}/details",
                json=details,
                timeout=60,
            ),
            "Could not update Play app details",
        )

        require(
            session.delete(
                f"{app_base}/edits/{edit_id}/listings/{LANGUAGE}/icon",
                timeout=60,
            ),
            "Could not clear existing Play icon",
            allowed=(200, 404),
        )

        uploaded = require(
            session.post(
                f"{UPLOAD_BASE}/applications/{PACKAGE}/edits/{edit_id}/listings/{LANGUAGE}/icon",
                params={"uploadType": "media"},
                headers={"Content-Type": "image/png"},
                data=ICON.read_bytes(),
                timeout=120,
            ),
            "Could not upload Play icon",
        ).json()
        icon_id = uploaded.get("image", {}).get("id")
        if not icon_id:
            raise SystemExit("Play icon upload did not return an image ID")

        require(
            session.post(f"{app_base}/edits/{edit_id}:validate", json={}, timeout=60),
            "Play rejected the store basics edit during validation",
        )
        require(
            session.post(f"{app_base}/edits/{edit_id}:commit", json={}, timeout=60),
            "Could not commit Play store basics edit",
        )
        committed = True
        edit_id = None

        verify_id = require(
            session.post(f"{app_base}/edits", json={}, timeout=60),
            "Could not create verification edit",
        ).json()["id"]
        try:
            verified_details = require(
                session.get(f"{app_base}/edits/{verify_id}/details", timeout=60),
                "Could not verify Play app details",
            ).json()
            images = require(
                session.get(
                    f"{app_base}/edits/{verify_id}/listings/{LANGUAGE}/icon",
                    timeout=60,
                ),
                "Could not verify Play icon",
            ).json().get("images", [])

            if verified_details.get("contactWebsite") != CONTACT_WEBSITE:
                raise SystemExit("Committed Play contact website did not verify")
            if verified_details.get("contactEmail") != CONTACT_EMAIL:
                raise SystemExit("Committed Play contact email did not verify")
            if len(images) != 1 or images[0].get("id") != icon_id:
                raise SystemExit("Committed Play icon did not verify")
        finally:
            session.delete(f"{app_base}/edits/{verify_id}", timeout=60)

        print("Play Store basics synced and independently verified.")
        print(f"Contact website: {CONTACT_WEBSITE}")
        print(f"Contact email: {CONTACT_EMAIL}")
        print(f"Store icon: {ICON} ({icon_id})")
    finally:
        if edit_id is not None and not committed:
            session.delete(f"{app_base}/edits/{edit_id}", timeout=60)


if __name__ == "__main__":
    main()
