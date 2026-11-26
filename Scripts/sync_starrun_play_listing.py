#!/usr/bin/env python3
"""Create/update only Star/Run's en-US Google Play listing and media.

All writes use transactional Android Publisher edits; existing live reviews are
never cancelled. Successful writes are independently read back after commit.
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

import requests
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DIR = ROOT / "play-store-starrun"
CONFIG = json.loads((DIR / "en-US.json").read_text())
PACKAGE = "com.rsm.starrun"
LANGUAGE = "en-US"
BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD_BASE = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"
ASSET = DIR / "generated"
TYPES = {
    "icon": [ASSET / "icon.png"],
    "featureGraphic": [ASSET / "feature-graphic.png"],
    "phoneScreenshots": sorted((ASSET / "screenshots").glob("*.png")),
}


def checked(response: requests.Response, label: str, status=(200,)) -> requests.Response:
    if response.status_code not in status:
        raise RuntimeError(f"{label} failed HTTP {response.status_code}: {response.text[:2400]}")
    return response


def inspect_assets() -> None:
    if CONFIG["packageName"] != PACKAGE or CONFIG["language"] != LANGUAGE:
        raise RuntimeError("Wrong package or Play listing language; refusing cross-app write")
    if not (0 < len(CONFIG["title"]) <= 30 and 0 < len(CONFIG["shortDescription"]) <= 80
            and 0 < len(CONFIG["fullDescription"]) <= 4000):
        raise RuntimeError("Play listing text exceeds Google length limits")
    if not 4 <= len(TYPES["phoneScreenshots"]) <= 8:
        raise RuntimeError("Expected four to eight real Star/Run game captures")
    for kind, files in TYPES.items():
        for path in files:
            if not path.is_file():
                raise RuntimeError(f"Missing image: {path}")
            with Image.open(path) as img:
                size = img.size
                if kind == "icon" and size != (512, 512):
                    raise RuntimeError(f"Icon is not 512x512: {size}")
                if kind == "featureGraphic" and size != (1024, 500):
                    raise RuntimeError(f"Feature graphic is not 1024x500: {size}")
                if kind == "phoneScreenshots" and size != (1170, 2532):
                    raise RuntimeError(f"Screenshot is not 1170x2532: {path} {size}")
                if img.format != "PNG":
                    raise RuntimeError(f"Only PNG media may be uploaded: {path}")
    print(f"Validated: 1 original icon, 1 feature graphic, {len(TYPES['phoneScreenshots'])} real gameplay screenshots")


def upload_image(session: requests.Session, edit: str, kind: str, path: Path) -> str:
    response = checked(
        session.post(
            f"{UPLOAD_BASE}/edits/{edit}/listings/{LANGUAGE}/{kind}",
            params={"uploadType": "media"},
            headers={"Content-Type": "image/png"},
            data=path.read_bytes(),
            timeout=150,
        ), f"Upload {kind} {path.name}"
    ).json()
    image_id = response.get("image", {}).get("id")
    if not image_id:
        raise RuntimeError(f"Google Play did not return image ID for {path.name}")
    print(f"Uploaded Star/Run {kind}: {path.name}")
    return str(image_id)


def commit_safely(session: requests.Session, edit: str) -> None:
    address = f"{BASE}/edits/{edit}:commit"
    first = session.post(address, json={}, params={"changesInReviewBehavior": "ERROR_IF_IN_REVIEW"}, timeout=80)
    if first.status_code == 200:
        return
    # Some consoles require storing content changes without submitting them for review.
    # Do not bypass or cancel another review or silently treat any other HTTP 400 as success.
    if (first.status_code == 400 and
            ("changesNotSentForReview" in first.text or "not sent for review" in first.text.lower())):
        retry = session.post(
            address, json={},
            params={"changesInReviewBehavior": "ERROR_IF_IN_REVIEW", "changesNotSentForReview": "true"},
            timeout=80,
        )
        checked(retry, "Save Play content without triggering review")
        print("Google Play saved the listing in Changes not yet sent for review.")
        return
    checked(first, "Commit Play listing without disturbing existing review")


def main() -> None:
    token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "").strip()
    if not token:
        raise RuntimeError("Missing Google Play OAuth access token")
    inspect_assets()
    session = requests.Session()
    session.headers["Authorization"] = "Bearer " + token
    edit = None
    committed = False
    image_ids: dict[str, list[str]] = {}
    try:
        edit = checked(session.post(f"{BASE}/edits", json={}, timeout=80), "Create Play edit").json()["id"]
        uri = f"{BASE}/edits/{edit}"
        listing_response = session.get(f"{uri}/listings/{LANGUAGE}", timeout=60)
        if listing_response.status_code == 404:
            previous_listing = {}
        else:
            previous_listing = checked(listing_response, "Read existing Star/Run Play listing").json()
        previous_title = previous_listing.get("title", "")
        if previous_title and previous_title != CONFIG["title"]:
            raise RuntimeError(f"Refusing to replace an unrelated Play listing title: {previous_title}")

        listing = {
            "language": LANGUAGE,
            "title": CONFIG["title"],
            "shortDescription": CONFIG["shortDescription"],
            "fullDescription": CONFIG["fullDescription"],
        }
        if previous_listing.get("video"):
            listing["video"] = previous_listing["video"]
        checked(session.put(f"{uri}/listings/{LANGUAGE}", json=listing, timeout=80), "Update Star/Run store copy")

        details_resp = session.get(f"{uri}/details", timeout=60)
        details = {} if details_resp.status_code == 404 else checked(details_resp, "Read Play details").json()
        updated_details = {
            "defaultLanguage": details.get("defaultLanguage") or LANGUAGE,
            "contactEmail": CONFIG["contactEmail"],
            "contactWebsite": CONFIG["contactWebsite"],
        }
        if details.get("contactPhone"):
            updated_details["contactPhone"] = details["contactPhone"]
        checked(session.put(f"{uri}/details", json=updated_details, timeout=80), "Update Play support details")

        for kind, paths in TYPES.items():
            checked(
                session.delete(f"{uri}/listings/{LANGUAGE}/{kind}", timeout=80),
                f"Clear old Star/Run {kind}",
                status=(200, 404),
            )
            image_ids[kind] = [upload_image(session, edit, kind, path) for path in paths]

        commit_safely(session, edit)
        committed = True
        edit = None

        verified_edit = checked(session.post(f"{BASE}/edits", json={}, timeout=80), "Open readback edit").json()["id"]
        try:
            verify_uri = f"{BASE}/edits/{verified_edit}"
            verified_listing = checked(session.get(f"{verify_uri}/listings/{LANGUAGE}", timeout=80), "Read back store copy").json()
            for name, expected in listing.items():
                if verified_listing.get(name) != expected:
                    raise RuntimeError(f"Google Play listing field {name} did not verify")
            verified_details = checked(session.get(f"{verify_uri}/details", timeout=60), "Read back support details").json()
            for name in ("contactWebsite", "contactEmail"):
                if verified_details.get(name) != updated_details[name]:
                    raise RuntimeError(f"Google Play support detail {name} did not verify")
            for kind, expected in image_ids.items():
                images = checked(session.get(f"{verify_uri}/listings/{LANGUAGE}/{kind}", timeout=80), f"Read back {kind}").json().get("images", [])
                actual = [str(img.get("id", "")) for img in images]
                if actual != expected:
                    raise RuntimeError(f"{kind} did not verify in expected screenshot order; expected={expected}, actual={actual}")
        finally:
            session.delete(f"{BASE}/edits/{verified_edit}", timeout=30)

        print("SUCCESS: Google Play Star/Run store copy, contact details, icon, feature graphic and screenshots saved and independently verified.")
        print(f"Package: {PACKAGE}; locale: {LANGUAGE}; title: {CONFIG['title']}")
        print(f"Privacy policy (manual Play Console field): {CONFIG['privacyPolicyUrl']}")
    finally:
        if edit is not None and not committed:
            session.delete(f"{BASE}/edits/{edit}", timeout=30)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, requests.RequestException) as exc:
        print(f"::error::{exc}", file=sys.stderr)
        raise SystemExit(1)
