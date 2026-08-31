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
# vendor-id suffix -> (display name, highest submittable score)
#
# Every board carries difficulty-weighted points rather than a percentage. A
# percentage saturates: on a ten-question round a great many players reach 100,
# and a board whose top thousand entries are identical has stopped ranking
# anyone.
#
# Category boards report a lifetime total, which only ever grows, so the range
# has to cover years of play -- a million is 400 perfect Hard rounds.
# The daily is one round, so a perfect 1,650 fits inside 2,000.
LEADERBOARDS = {
    "football": ("Football High Scores", 1_000_000),
    "basketball": ("Basketball High Scores", 1_000_000),
    "soccer": ("Soccer High Scores", 1_000_000),
    "flags": ("World Flags High Scores", 1_000_000),
    "history": ("History High Scores", 1_000_000),
    "science": ("Science High Scores", 1_000_000),
    "movies": ("Movies High Scores", 1_000_000),
    "tv": ("TV High Scores", 1_000_000),
    "geography": ("Geography High Scores", 1_000_000),
    "music": ("Music High Scores", 1_000_000),
    "animals": ("Animals High Scores", 1_000_000),
    "food": ("Food & Drink High Scores", 1_000_000),
    "literature": ("Books & Literature High Scores", 1_000_000),
    "art": ("Art & Architecture High Scores", 1_000_000),
    "daily": ("Daily Challenge", 2_000),
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

    def try_request(self, method: str, path: str, **kwargs) -> tuple[bool, str]:
        """A request whose failure is reported to the caller rather than fatal.

        `request` exits the process on any HTTP error, which is right for the
        calls this script cannot continue without. It is wrong for the score
        range update: aborting there would leave the remaining leaderboards
        untouched and the daily one uncreated, turning a small fixable problem
        into a bigger one.
        """
        response = self.session.request(method, f"{BASE_URL}{path}", timeout=30, **kwargs)
        if response.status_code >= 300:
            return False, f"HTTP {response.status_code}: {response.text[:300]}"
        return True, ""

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

    range_failures: list[tuple[str, object, int, str]] = []

    for category, (display_name, score_range_end) in LEADERBOARDS.items():
        vendor_id = f"EZTrivia.{category}"
        leaderboard = by_vendor_id.get(vendor_id)
        if leaderboard:
            print(f"exists:  {vendor_id}")
            current_end = leaderboard["attributes"].get("scoreRangeEnd")
            if str(current_end) != str(score_range_end):
                patch = {
                    "data": {
                        "type": "gameCenterLeaderboards",
                        "id": leaderboard["id"],
                        "attributes": {"scoreRangeEnd": str(score_range_end)},
                    }
                }
                ok, error = api.try_request(
                    "PATCH", f"/gameCenterLeaderboards/{leaderboard['id']}", json=patch
                )
                if ok:
                    print(f"         score range {current_end} -> {score_range_end}")
                else:
                    range_failures.append((vendor_id, current_end, score_range_end, error))
                    print(f"         COULD NOT widen score range from {current_end}: {error}")
        else:
            body = {
                "data": {
                    "type": "gameCenterLeaderboards",
                    "attributes": {
                        "referenceName": display_name,
                        "vendorIdentifier": vendor_id,
                        "submissionType": "BEST_SCORE",
                        "scoreSortType": "DESC",
                        # Strings, not integers. The API rejects integers with
                        # ENTITY_ERROR.ATTRIBUTE.TYPE: "Expected a STRING but
                        # got INTEGER". The football leaderboard that already
                        # exists reads back as '0'/'100', confirming the type.
                        "scoreRangeStart": "0",
                        "scoreRangeEnd": str(score_range_end),
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

    if range_failures:
        # Not a warning. A board still capped at 100 rejects every real
        # submission, and the app would look like it had simply stopped
        # recording scores.
        print("\nSCORE RANGES NOT UPDATED — fix these before submitting:")
        for vendor_id, current_end, wanted, error in range_failures:
            print(f"  {vendor_id}: is {current_end}, needs {wanted}  ({error})")
        print(
            "\nSet 'Score range' on each board in App Store Connect "
            "(Game Center -> Leaderboards) if the API will not."
        )
        raise SystemExit(1)

    print(f"Game Center configuration complete: {len(LEADERBOARDS)} leaderboards.")


if __name__ == "__main__":
    main()
