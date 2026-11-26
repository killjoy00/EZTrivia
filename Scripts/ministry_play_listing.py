#!/usr/bin/env python3
"""Inspect or sync only The Ministry of Coincidences' English Play listing.

No screenshots, content ratings, public release tracks, or other apps are changed.
"""
import json
import os
import sys
from pathlib import Path

import requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

PACKAGE = "org.killjoy00.ministry"
LOCALE = "en-US"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PACKAGE

def checked(resp, action, allow=(200,)):
    if resp.status_code not in allow:
        raise RuntimeError(f"{action}: HTTP {resp.status_code}: {resp.text[:450]}")
    return resp

def main():
    meta = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    assert meta["bundleID"] == PACKAGE and meta["locale"] == LOCALE
    assert 0 < len(meta["appName"]) <= 30
    assert 0 < len(meta["description"]) <= 4000
    mode = os.environ.get("SYNC_MODE")
    assert mode in ("inspect", "apply")
    cred = json.loads(os.environ["GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"])
    credentials = service_account.Credentials.from_service_account_info(
        cred, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    credentials.refresh(Request())
    session = requests.Session()
    session.headers["Authorization"] = "Bearer " + credentials.token
    desired = {
        "title": meta["appName"],
        "shortDescription": "Small coincidences. Outrageous consequences.",
        "fullDescription": meta["description"],
    }
    edit = checked(session.post(BASE + "/edits", json={}, timeout=45), "Create edit").json()["id"]
    committed = False
    try:
        root = BASE + "/edits/" + edit
        r = session.get(root + "/listings/" + LOCALE, timeout=45)
        if r.status_code == 404:
            actual = {}
        else:
            actual = checked(r, "Read listing").json()
        different = [k for k, v in desired.items() if actual.get(k) != v]
        print(f"Ministry Play en-US listing; mode={mode}; changes={','.join(different) if different else 'none'}")
        if mode == "apply" and different:
            checked(session.put(root + "/listings/" + LOCALE, json=desired, timeout=60), "Update listing")
            checked(session.post(root + ":validate", json={}, timeout=60), "Validate edit")
            checked(session.post(root + ":commit",
                params={"changesInReviewBehavior": "ERROR_IF_IN_REVIEW"},
                json={}, timeout=60), "Commit listing")
            committed = True
            verify = checked(session.post(BASE + "/edits", json={}, timeout=45), "Open verification edit").json()["id"]
            try:
                after = checked(session.get(BASE + "/edits/" + verify + "/listings/" + LOCALE, timeout=45), "Verify listing").json()
                assert all(after.get(k) == v for k, v in desired.items()), "Listing readback mismatch"
                print("Verified Ministry Play listing text saved")
            finally:
                session.delete(BASE + "/edits/" + verify, timeout=30)
        elif mode == "inspect":
            print("Inspect only: no Play changes made")
        else:
            print("Listing already matches; no Play change required")
    finally:
        if not committed:
            session.delete(BASE + "/edits/" + edit, timeout=30)

if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, KeyError, AssertionError, requests.RequestException) as exc:
        print("::error::" + str(exc), file=sys.stderr)
        raise SystemExit(1)
