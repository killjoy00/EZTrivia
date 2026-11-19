#!/usr/bin/env python3
"""Read-only post-upload audit of Star/Run's Google Play Internal release."""
from __future__ import annotations
import json
import os
from pathlib import Path
import requests

PACKAGE = "com.rsm.starrun"
BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"

def get(session, url, label):
    r = session.get(url, timeout=70)
    if r.status_code != 200:
        raise SystemExit(f"{label}: HTTP {r.status_code}: {r.text[:1500]}")
    return r.json()

def main():
    request = json.loads(Path(".github/starrun-android-release-request.json").read_text())
    expected_code = str(request["version_code"])
    token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "")
    if not token:
        raise SystemExit("Missing Play API token")
    s = requests.Session()
    s.headers["Authorization"] = "Bearer " + token
    r = s.post(f"{BASE}/edits", json={}, timeout=70)
    if r.status_code != 200:
        raise SystemExit(f"Could not open Play read-only edit: {r.status_code}: {r.text[:1300]}")
    edit = r.json()["id"]
    try:
        root = f"{BASE}/edits/{edit}"
        track = get(s, f"{root}/tracks/internal", "Internal testing track")
        bundles = get(s, f"{root}/bundles", "Existing Android App Bundles")
        listing = get(s, f"{root}/listings/en-US", "English Play listing")
        details = get(s, f"{root}/details", "Play contact details")
        images = {kind: get(s, f"{root}/listings/en-US/{kind}", kind).get("images", [])
                  for kind in ("icon", "featureGraphic", "phoneScreenshots")}
        codes = [str(code) for rel in track.get("releases", []) for code in rel.get("versionCodes", [])]
        bundle_codes = [str(b.get("versionCode")) for b in bundles.get("bundles", [])]
        print("STAR_RUN_PLAY_AUDIT_BEGIN")
        print("package=" + PACKAGE)
        print("internal_release_codes=" + ",".join(codes))
        print("internal_release_statuses=" + ",".join(str(x.get("status")) for x in track.get("releases", [])))
        print("uploaded_bundle_codes=" + ",".join(bundle_codes))
        print("title=" + str(listing.get("title")))
        print("short_description_length=" + str(len(listing.get("shortDescription", ""))))
        print("full_description_length=" + str(len(listing.get("fullDescription", ""))))
        print("support_website=" + str(details.get("contactWebsite")))
        print("support_email_present=" + str(bool(details.get("contactEmail"))))
        print("icon_count=" + str(len(images["icon"])))
        print("feature_graphic_count=" + str(len(images["featureGraphic"])))
        print("phone_screenshots_count=" + str(len(images["phoneScreenshots"])))
        print("STAR_RUN_PLAY_AUDIT_END")
        if expected_code not in bundle_codes or not any(
            expected_code in [str(c) for c in release.get("versionCodes", [])]
            and release.get("status") == "completed"
            for release in track.get("releases", [])
        ):
            raise SystemExit(
                f"Expected Android v{expected_code} was not confirmed in uploaded "
                "bundles and a completed Internal testing release"
            )
        if listing.get("title") != "Star/Run":
            raise SystemExit("Play listing title is not Star/Run")
        if len(images["icon"]) != 1 or len(images["featureGraphic"]) != 1 or len(images["phoneScreenshots"]) != 8:
            raise SystemExit("Expected icon/feature graphic/eight screenshots were not all present")
        if not listing.get("shortDescription") or not listing.get("fullDescription") or not details.get("contactEmail"):
            raise SystemExit("Play listing/support fields incomplete")
        print(f"PASS: Google Play independently confirms Star/Run Android v{expected_code} on Internal track and all listing media.")
    finally:
        s.delete(f"{BASE}/edits/{edit}", timeout=30)

if __name__ == "__main__":
    main()
