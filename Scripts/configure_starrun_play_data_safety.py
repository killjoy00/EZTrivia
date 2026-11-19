#!/usr/bin/env python3
"""Google Play Data safety for the *currently shipped* ad-free Star/Run Android build.

StarRun Android 1.3 has no Android AdMob, Play Games, purchase SDK, account,
developer backend, or network submission of local saves. Do not reuse the
EZTrivia declaration; that app includes advertising/Play Games/Play Billing.
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import os
from pathlib import Path

import requests
from configure_play_data_safety import (
    R, V,
    fetch_template, index_rows, answer, select, render,
)

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "play-store-starrun/data-safety.csv"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.rsm.starrun/dataSafety"


def build() -> str:
    rows, fields = fetch_template()
    # Every Google sample answer must be blanked before setting Star/Run's own data.
    for row in rows:
        row[V] = ""
    idx = index_rows(rows)
    answer(idx, "PSL_DATA_COLLECTION_COLLECTS_PERSONAL_DATA", "", False)
    # No outbound user-data transfer in the Android game. Transit encryption is
    # conditional on a YES answer to collection; do not assert a nonexistent flow.
    # Google's live API forbids account-creation and deletion selections
    # when no personal/user data is collected. Leave both conditional groups blank.
    # The local-only game has no account or backend.
    answer(idx, "PSL_HAS_OUTSIDE_APP_ACCOUNTS", "", False)
    types = [r[R] for r in rows if r.get(V) == "TRUE" and r.get(R, "").startswith("PSL_DATA_TYPES_")]
    if types:
        raise RuntimeError("No Star/Run Android data categories may be declared collected")
    if idx[("PSL_DATA_COLLECTION_COLLECTS_PERSONAL_DATA", "")][V] != "FALSE":
        raise RuntimeError("No-data-collected declaration failed")
    # For a no-collection app, Google's API requires account creation and
    # deletion sections to have NO selected answers (not even "None").
    selected = [(r.get("Question ID (machine readable)", ""), r.get(R, ""))
                for r in rows if r[V] == "TRUE"]
    if selected:
        raise RuntimeError(f"Unexpected affirmative Data safety answers: {selected}")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    result = render(rows, fields)
    OUTPUT.write_text(result, encoding="utf-8")
    print(f"Prepared Star/Run ad-free Android Data safety CSV: {len(rows)} rows")
    print("No app-user data collected or shared; no account creation, ads, billing, or Play Games")
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--submit", action="store_true")
    args = parser.parse_args()
    csv_text = build()
    if not args.submit:
        print("Dry run only; no Google Play change")
        return
    token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "")
    if not token:
        raise SystemExit("Cannot submit without Google Play access token")
    response = requests.post(
        API,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        data=json.dumps({"safetyLabels": csv_text}), timeout=150,
    )
    if not response.ok:
        raise SystemExit(f"Star/Run Data safety submission rejected HTTP {response.status_code}: {response.text[:2600]}")
    print(f"SUCCESS: Google Play Data safety declaration for com.rsm.starrun accepted (HTTP {response.status_code})")


if __name__ == "__main__":
    main()
