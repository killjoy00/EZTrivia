#!/usr/bin/env python3
"""Audit EZ Trivia Google Play Games Services configuration via the Publishing API.

This is intentionally read-only. It compares the live achievement/leaderboard
resource IDs against the IDs compiled into the Android client and records whether
Google currently exposes draft and/or published metadata for each resource.

Saved Games enablement is not exposed by this Publishing API and is therefore
reported as an explicit account-side/runtime check rather than guessed here.
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
IDS_FILE = ROOT / "android/app/src/main/java/com/rsm/eztrivia/playgames/PlayGamesIds.kt"
APPLICATION_ID = "406580555223"
API_ROOT = "https://www.googleapis.com/games/v1configuration"


def get_json(url: str, token: str) -> dict[str, Any]:
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "User-Agent": "EZTrivia-PlayGames-Audit/1.0",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} for {url}: {body}") from exc


def list_all(resource: str, token: str) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    page_token = ""
    while True:
        params = {"maxResults": "200"}
        if page_token:
            params["pageToken"] = page_token
        url = (
            f"{API_ROOT}/applications/{APPLICATION_ID}/{resource}?"
            + urllib.parse.urlencode(params)
        )
        payload = get_json(url, token)
        items.extend(payload.get("items", []))
        page_token = str(payload.get("nextPageToken", "")).strip()
        if not page_token:
            return items


def parse_expected_ids() -> tuple[set[str], set[str]]:
    text = IDS_FILE.read_text(encoding="utf-8")

    achievement_match = re.search(
        r"val achievements: Map<String, String> = mapOf\((.*?)\n\s*\)\n\n\s*/\*\* Standard achievements",
        text,
        flags=re.S,
    )
    if not achievement_match:
        raise RuntimeError("Could not parse achievement ID map from PlayGamesIds.kt")
    achievement_ids = set(re.findall(r'"(CgkI[^"\\]+)"', achievement_match.group(1)))

    daily_match = re.search(r'const val dailyLeaderboard = "([^"]+)"', text)
    leaderboard_match = re.search(
        r"val categoryLeaderboards: Map<TriviaCategory, String> = mapOf\((.*?)\n\s*\)\n}",
        text,
        flags=re.S,
    )
    if not daily_match or not leaderboard_match:
        raise RuntimeError("Could not parse leaderboard IDs from PlayGamesIds.kt")
    leaderboard_ids = {daily_match.group(1)}
    leaderboard_ids.update(re.findall(r'"(CgkI[^"\\]+)"', leaderboard_match.group(1)))

    if len(achievement_ids) != 19:
        raise RuntimeError(f"Expected 19 compiled achievement IDs, found {len(achievement_ids)}")
    if len(leaderboard_ids) != 17:
        raise RuntimeError(f"Expected 17 compiled leaderboard IDs, found {len(leaderboard_ids)}")
    return achievement_ids, leaderboard_ids


def summarize(items: list[dict[str, Any]], expected_ids: set[str]) -> dict[str, Any]:
    live_ids = {str(item.get("id", "")) for item in items if item.get("id")}
    published_ids = sorted(
        str(item["id"]) for item in items if item.get("id") and item.get("published") is not None
    )
    draft_ids = sorted(
        str(item["id"]) for item in items if item.get("id") and item.get("draft") is not None
    )
    draft_only_ids = sorted(
        str(item["id"])
        for item in items
        if item.get("id") and item.get("draft") is not None and item.get("published") is None
    )
    published_only_ids = sorted(
        str(item["id"])
        for item in items
        if item.get("id") and item.get("published") is not None and item.get("draft") is None
    )
    both_ids = sorted(
        str(item["id"])
        for item in items
        if item.get("id") and item.get("published") is not None and item.get("draft") is not None
    )

    return {
        "count": len(items),
        "expectedCount": len(expected_ids),
        "missingExpectedIds": sorted(expected_ids - live_ids),
        "unexpectedLiveIds": sorted(live_ids - expected_ids),
        "publishedMetadataCount": len(published_ids),
        "draftMetadataCount": len(draft_ids),
        "draftOnlyCount": len(draft_only_ids),
        "publishedOnlyCount": len(published_only_ids),
        "publishedAndDraftCount": len(both_ids),
        "draftOnlyIds": draft_only_ids,
        "publishedOnlyIds": published_only_ids,
        "publishedAndDraftIds": both_ids,
    }


def main() -> int:
    token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "").strip()
    if not token:
        raise RuntimeError("GOOGLE_PLAY_ACCESS_TOKEN is missing")

    achievement_ids, leaderboard_ids = parse_expected_ids()
    achievements = list_all("achievements", token)
    leaderboards = list_all("leaderboards", token)

    achievement_summary = summarize(achievements, achievement_ids)
    leaderboard_summary = summarize(leaderboards, leaderboard_ids)

    achievement_types: dict[str, int] = {}
    for item in achievements:
        value = str(item.get("achievementType", "UNKNOWN"))
        achievement_types[value] = achievement_types.get(value, 0) + 1

    result = {
        "applicationId": APPLICATION_ID,
        "compiledConfig": {
            "achievementCount": len(achievement_ids),
            "leaderboardCount": len(leaderboard_ids),
        },
        "achievements": achievement_summary | {"types": achievement_types},
        "leaderboards": leaderboard_summary,
        "savedGames": {
            "auditableViaPublishingApi": False,
            "note": (
                "Saved Games enablement is not exposed by the Play Games Services Publishing API. "
                "Confirm it in Play Console and with a Play-installed two-device runtime test."
            ),
        },
    }

    if achievement_summary["missingExpectedIds"] or achievement_summary["unexpectedLiveIds"]:
        raise RuntimeError(
            "Play Games achievement IDs do not match the Android client: "
            + json.dumps(achievement_summary, sort_keys=True)
        )
    if leaderboard_summary["missingExpectedIds"] or leaderboard_summary["unexpectedLiveIds"]:
        raise RuntimeError(
            "Play Games leaderboard IDs do not match the Android client: "
            + json.dumps(leaderboard_summary, sort_keys=True)
        )

    output = Path(os.environ.get("PLAY_GAMES_AUDIT_OUTPUT", "play-games-audit.json"))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(json.dumps(result, indent=2, sort_keys=True))
    print(f"Wrote Play Games audit: {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
