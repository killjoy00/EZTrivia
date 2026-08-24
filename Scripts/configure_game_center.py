#!/usr/bin/env python3
"""Idempotently create EZ Trivia's classic Game Center leaderboards."""

from __future__ import annotations

import os
import sys
import time
from pathlib import Path

import jwt
import requests


BASE_URL = "https://api.appstoreconnect.apple.com/v1"
BUNDLE_ID = "com.rsm.eztrivia"
LEADERBOARDS = {
    "football": "Football High Scores",
    "basketball": "Basketball High Scores",
    "soccer": "Soccer High Scores",
    "flags": "World Flags High Scores",
    "history": "History High Scores",
    "science": "Science High Scores",
    "movies": "Movies High Scores",
    "geography": "Geography High Scores",
    "music": "Music High Scores",
    "animals": "Animals High Scores",
    "food": "Food & Drink High Scores",
}


def required_environment(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def api_token() -> str:
    key_id = required_environment("ASC_KEY_ID")
    issuer_id = required_environment("ASC_ISSUER_ID")
    key_path = Path(os.path.expanduser(os.environ.get(
        "ASC_KEY_PATH", f"~/private_keys/AuthKey_{key_id}.p8"
    )))
    if not key_path.is_file():
        raise SystemExit(f"App Store Connect key not found: {key_path}")
    now = int(time.time())
    return jwt.encode(
        {"iss": issuer_id, "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"},
        key_path.read_text(),
        algorithm="ES256",
        headers={"kid": key_id},
    )


class API:
    def __init__(self) -> None:
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": f"Bearer {api_token()}",
            "Content-Type": "application/json",
        })

    def request(self, method: str, path: str, **kwargs) -> dict:
        response = self.session.request(method, f"{BASE_URL}{path}", timeout=30, **kwargs)
        if response.status_code >= 300:
            print(f"{method} {path} failed: HTTP {response.status_code}", file=sys.stderr)
            print(response.text[:2000], file=sys.stderr)
            raise SystemExit(1)
        return response.json() if response.content else {}

    def get_all(self, path: str, **params) -> list[dict]:
        items: list[dict] = []
        url: str | None = f"{BASE_URL}{path}"
        while url:
            response = self.session.get(url, params=params, timeout=30)
            if response.status_code >= 300:
                print(f"GET {path} failed: HTTP {response.status_code}", file=sys.stderr)
                print(response.text[:2000], file=sys.stderr)
                raise SystemExit(1)
            body = response.json()
            items.extend(body.get("data", []))
            url = body.get("links", {}).get("next")
            params = {}
        return items


def main() -> None:
    api = API()
    apps = api.get_all("/apps", **{"filter[bundleId]": BUNDLE_ID})
    if len(apps) != 1:
        raise SystemExit(f"Expected one App Store Connect app for {BUNDLE_ID}; found {len(apps)}")
    app_id = apps[0]["id"]
    detail = api.request("GET", f"/apps/{app_id}/gameCenterDetail").get("data")
    if not detail:
        # Do not assert a cause here. This response is empty in several
        # different situations, and an earlier version of this message named
        # one of them ("enable Game Center for the bundle ID") confidently
        # enough to send a human off disabling and re-enabling a capability
        # that was already on. Report what was observed and list what to look
        # at, rather than guessing which one it is.
        raise SystemExit(
            f"App Store Connect returned no gameCenterDetail for {BUNDLE_ID}.\n"
            "That happens when Game Center has never been configured for the app, "
            "when the capability is off for the bundle ID, or when this API key "
            "cannot see the app's Game Center data.\n"
            "Check, in order: the app's Game Center page in App Store Connect; "
            "the Game Center capability under Certificates, Identifiers & Profiles; "
            "and the API key's role.\n"
            "The 'Check TestFlight status' workflow reports all three."
        )
    detail_id = detail["id"]
    existing = api.get_all(f"/gameCenterDetails/{detail_id}/gameCenterLeaderboards")
    by_vendor_id = {item["attributes"]["vendorIdentifier"]: item for item in existing}

    for category, display_name in LEADERBOARDS.items():
        vendor_id = f"EZTrivia.{category}"
        leaderboard = by_vendor_id.get(vendor_id)
        if leaderboard:
            print(f"exists:  {vendor_id}")
        else:
            body = {
                "data": {
                    "type": "gameCenterLeaderboards",
                    "attributes": {
                        "referenceName": display_name,
                        "vendorIdentifier": vendor_id,
                        "submissionType": "BEST_SCORE",
                        "scoreSortType": "DESC",
                        "scoreRangeStart": 0,
                        "scoreRangeEnd": 100,
                        "defaultFormatter": "INTEGER",
                    },
                    "relationships": {
                        "gameCenterDetail": {
                            "data": {"type": "gameCenterDetails", "id": detail_id}
                        }
                    },
                }
            }
            leaderboard = api.request("POST", "/gameCenterLeaderboards", json=body)["data"]
            print(f"created: {vendor_id}")

        leaderboard_id = leaderboard["id"]
        localizations = api.get_all(f"/gameCenterLeaderboards/{leaderboard_id}/localizations")
        if any(item["attributes"].get("locale") == "en-US" for item in localizations):
            print(f"         en-US localization exists")
            continue
        localization_body = {
            "data": {
                "type": "gameCenterLeaderboardLocalizations",
                "attributes": {"locale": "en-US", "name": display_name},
                "relationships": {
                    "gameCenterLeaderboard": {
                        "data": {"type": "gameCenterLeaderboards", "id": leaderboard_id}
                    }
                },
            }
        }
        api.request("POST", "/gameCenterLeaderboardLocalizations", json=localization_body)
        print(f"         created en-US localization")

    print(f"Game Center configuration complete: {len(LEADERBOARDS)} leaderboards.")


if __name__ == "__main__":
    main()
