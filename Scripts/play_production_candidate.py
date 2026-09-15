#!/usr/bin/env python3
"""Audit, validate, or promote an existing Internal Testing bundle to Production.

This script never uploads a new binary. It only works with a versionCode that is
already accepted by Google Play and present in a completed Internal release.

`audit` is read-only apart from creating/deleting a disposable Play edit.
`validate` stages the intended Production track change in that disposable edit,
asks Google Play to validate it, and then deletes the edit without committing.
`promote` performs the same checks and commits only when the caller also sets
CONFIRM_PRODUCTION=true.

Production readiness also includes the live Play Games Services Publishing API
state. EZ Trivia intentionally refuses to validate/promote while any compiled
achievement or leaderboard exists only as draft metadata, because unpublished
PGS projects reject non-tester accounts at platform authentication time.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

import audit_play_games

PACKAGE_NAME = "com.rsm.eztrivia"
API_ROOT = "https://androidpublisher.googleapis.com/androidpublisher/v3"
# versionCode 2 predates the signed-release guard that requires production AdMob IDs.
MIN_PRODUCTION_CANDIDATE_VERSION = 3


def api_request(
    token: str,
    url: str,
    *,
    method: str = "GET",
    payload: dict[str, Any] | None = None,
    allow_404: bool = False,
) -> dict[str, Any]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            **({"Content-Type": "application/json"} if payload is not None else {}),
            "User-Agent": "EZTrivia-Play-Production/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        if allow_404 and exc.code == 404:
            return {}
        error_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} for {method} {url}: {error_body}") from exc


def delete_edit(token: str, edit_url: str) -> None:
    request = urllib.request.Request(
        edit_url,
        method="DELETE",
        headers={"Authorization": f"Bearer {token}"},
    )
    try:
        urllib.request.urlopen(request, timeout=30).read()
    except Exception as exc:  # cleanup should not hide the real result
        print(f"WARNING: could not delete disposable Play edit: {exc}", file=sys.stderr)


def version_codes(release: dict[str, Any]) -> list[int]:
    values: list[int] = []
    for value in release.get("versionCodes", []) or []:
        try:
            values.append(int(value))
        except (TypeError, ValueError):
            continue
    return values


def completed_internal_releases(track: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        release
        for release in (track.get("releases", []) or [])
        if release.get("status") == "completed"
    ]


def find_release(releases: list[dict[str, Any]], candidate: int) -> dict[str, Any] | None:
    return next((release for release in releases if candidate in version_codes(release)), None)


def audit_play_games_publication(token: str) -> dict[str, Any]:
    achievement_ids, leaderboard_ids = audit_play_games.parse_expected_ids()
    achievements = audit_play_games.list_all("achievements", token)
    leaderboards = audit_play_games.list_all("leaderboards", token)
    achievement_summary = audit_play_games.summarize(achievements, achievement_ids)
    leaderboard_summary = audit_play_games.summarize(leaderboards, leaderboard_ids)

    return {
        "applicationId": audit_play_games.APPLICATION_ID,
        "achievements": achievement_summary,
        "leaderboards": leaderboard_summary,
        "allCompiledResourcesPublished": (
            achievement_summary["publishedMetadataCount"] == achievement_summary["expectedCount"]
            and leaderboard_summary["publishedMetadataCount"] == leaderboard_summary["expectedCount"]
            and not achievement_summary["missingExpectedIds"]
            and not achievement_summary["unexpectedLiveIds"]
            and not leaderboard_summary["missingExpectedIds"]
            and not leaderboard_summary["unexpectedLiveIds"]
        ),
        "savedGamesEnablementAuditableViaPublishingApi": False,
    }


def write_output(path: Path, result: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    print(f"Wrote production-candidate evidence: {path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("audit", "validate", "promote"), default="audit")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--output", default="play-production-candidate.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "").strip()
    if not token:
        raise RuntimeError("GOOGLE_PLAY_ACCESS_TOKEN is missing")

    output = Path(args.output)
    app_base = f"{API_ROOT}/applications/{PACKAGE_NAME}"
    edit = api_request(token, f"{app_base}/edits", method="POST", payload={})
    edit_id = str(edit.get("id", "")).strip()
    if not edit_id:
        raise RuntimeError("Google Play did not return an edit ID")

    edit_base = f"{app_base}/edits/{edit_id}"
    committed = False
    try:
        bundles = api_request(token, f"{edit_base}/bundles").get("bundles", []) or []
        internal = api_request(token, f"{edit_base}/tracks/internal", allow_404=True)
        production = api_request(token, f"{edit_base}/tracks/production", allow_404=True)
        play_games = audit_play_games_publication(token)

        bundle_versions = sorted(
            int(bundle["versionCode"])
            for bundle in bundles
            if str(bundle.get("versionCode", "")).isdigit()
        )
        internal_completed = completed_internal_releases(internal)
        internal_completed_versions = sorted(
            {version for release in internal_completed for version in version_codes(release)}
        )
        highest_internal = max(internal_completed_versions, default=0)
        candidate = args.version_code or highest_internal
        candidate_release = find_release(internal_completed, candidate)

        production_releases = production.get("releases", []) or []
        production_versions = sorted(
            {version for release in production_releases for version in version_codes(release)}
        )
        blockers: list[str] = []

        if candidate <= 0:
            blockers.append("No completed Internal versionCode is available to promote.")
        if candidate < MIN_PRODUCTION_CANDIDATE_VERSION:
            blockers.append(
                f"versionCode {candidate} predates the production-AdMob signed-release guard; "
                f"automated Production promotion requires versionCode >= {MIN_PRODUCTION_CANDIDATE_VERSION}."
            )
        if candidate not in bundle_versions:
            blockers.append(f"versionCode {candidate} is not an accepted Play bundle.")
        if candidate not in internal_completed_versions:
            blockers.append(f"versionCode {candidate} is not in a completed Internal Testing release.")
        if highest_internal and candidate != highest_internal:
            blockers.append(
                f"versionCode {candidate} is not the highest completed Internal versionCode ({highest_internal})."
            )
        if candidate_release is not None:
            release_name = str(candidate_release.get("name", ""))
            if f"(v{candidate})" not in release_name:
                blockers.append(
                    f"Internal release name does not contain the expected workflow provenance marker '(v{candidate})'."
                )
        if candidate in production_versions:
            blockers.append(f"versionCode {candidate} is already present on Production.")

        blocking_prod_statuses = sorted(
            {
                str(release.get("status", "unknown"))
                for release in production_releases
                if release.get("status") not in (None, "completed")
            }
        )
        if blocking_prod_statuses:
            blockers.append(
                "Production already has a non-completed release state that this workflow will not overwrite: "
                + ", ".join(blocking_prod_statuses)
            )
        if production_versions and max(production_versions) > candidate:
            blockers.append(
                f"Production already contains a higher versionCode ({max(production_versions)}) than candidate {candidate}."
            )

        achievement_summary = play_games["achievements"]
        leaderboard_summary = play_games["leaderboards"]
        if not play_games["allCompiledResourcesPublished"]:
            blockers.append(
                "Google Play Games Services is not production-published for every compiled resource: "
                f"achievements {achievement_summary['publishedMetadataCount']}/{achievement_summary['expectedCount']} published, "
                f"leaderboards {leaderboard_summary['publishedMetadataCount']}/{leaderboard_summary['expectedCount']} published. "
                "Complete runtime PGS/Saved Games testing first, then publish PGS before Production validation/promotion."
            )

        if args.mode == "promote" and os.environ.get("CONFIRM_PRODUCTION", "").lower() != "true":
            blockers.append("promote mode requires CONFIRM_PRODUCTION=true.")

        result: dict[str, Any] = {
            "packageName": PACKAGE_NAME,
            "mode": args.mode,
            "minimumProductionCandidateVersionCode": MIN_PRODUCTION_CANDIDATE_VERSION,
            "candidateVersionCode": candidate,
            "acceptedBundleVersionCodes": bundle_versions,
            "internalCompletedVersionCodes": internal_completed_versions,
            "highestInternalCompletedVersionCode": highest_internal,
            "candidateInternalRelease": candidate_release,
            "productionVersionCodes": production_versions,
            "productionReleases": production_releases,
            "playGames": play_games,
            "blockers": blockers,
            "promotable": not blockers,
            "validated": False,
            "committed": False,
        }
        write_output(output, result)

        if args.mode == "audit":
            return 0
        if blockers:
            raise RuntimeError("Production promotion blocked: " + " | ".join(blockers))
        if candidate_release is None:
            raise RuntimeError("Internal candidate release disappeared during validation")

        production_release: dict[str, Any] = {
            "name": f"{candidate_release.get('name', f'EZ Trivia v{candidate}')} [Production]",
            "versionCodes": [str(candidate)],
            "status": "completed",
        }
        if candidate_release.get("releaseNotes"):
            production_release["releaseNotes"] = candidate_release["releaseNotes"]

        api_request(
            token,
            f"{edit_base}/tracks/production",
            method="PUT",
            payload={"track": "production", "releases": [production_release]},
        )
        api_request(token, f"{edit_base}:validate", method="POST", payload={})
        result["validated"] = True
        write_output(output, result)

        if args.mode == "validate":
            return 0

        api_request(token, f"{edit_base}:commit", method="POST", payload={})
        committed = True
        result["committed"] = True
        write_output(output, result)
        return 0
    finally:
        if not committed:
            delete_edit(token, edit_base)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
