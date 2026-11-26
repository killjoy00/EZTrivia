#!/usr/bin/env python3
"""Create or verify EZ Trivia's Android AdMob app and banner ad unit.

AdMob's inventory API requires OAuth from an authenticated AdMob user; the
Google Play service account cannot authorize these calls. This script accepts
an OAuth client/refresh-token bundle from the environment, validates the
publisher account used by EZ Trivia, discovers existing Android inventory, and
optionally creates anything missing.

The v1beta app/ad-unit create methods are limited-access AdMob API methods. If
Google returns 403, the script explains that the app/ad unit must be created in
the AdMob UI (or API access enabled by the AdMob account manager) and can then
be re-run in verification mode.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

TOKEN_URL = "https://oauth2.googleapis.com/token"
API_ROOT = "https://admob.googleapis.com/v1beta"
DEFAULT_PUBLISHER_ID = "pub-1217971050094766"
PACKAGE_NAME = "com.rsm.eztrivia"
APP_DISPLAY_NAME = "EZ Trivia"
BANNER_DISPLAY_NAME = "EZ Trivia Android Banner"
APP_ADS_URL = "https://killjoy00.github.io/app-ads.txt"
APP_ADS_AUTHORITY_ID = "f08c47fec0942fa0"


class ApiError(RuntimeError):
    def __init__(self, status: int, url: str, body: str):
        super().__init__(f"HTTP {status} for {url}: {body}")
        self.status = status
        self.url = url
        self.body = body


def request_json(
    url: str,
    *,
    method: str = "GET",
    token: str | None = None,
    payload: dict[str, Any] | None = None,
    form: dict[str, str] | None = None,
) -> dict[str, Any]:
    headers = {"Accept": "application/json"}
    data: bytes | None = None

    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode("utf-8")
    elif form is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        data = urllib.parse.urlencode(form).encode("utf-8")

    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise ApiError(exc.code, url, body) from exc


def load_oauth_credentials() -> dict[str, str]:
    raw = os.environ.get("ADMOB_OAUTH_CREDENTIALS_JSON", "").strip()
    if raw:
        try:
            values = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise RuntimeError("ADMOB_OAUTH_CREDENTIALS_JSON is not valid JSON") from exc
    else:
        values = {
            "client_id": os.environ.get("ADMOB_OAUTH_CLIENT_ID", ""),
            "client_secret": os.environ.get("ADMOB_OAUTH_CLIENT_SECRET", ""),
            "refresh_token": os.environ.get("ADMOB_OAUTH_REFRESH_TOKEN", ""),
        }

    required = ("client_id", "client_secret", "refresh_token")
    missing = [key for key in required if not str(values.get(key, "")).strip()]
    if missing:
        raise RuntimeError(
            "Missing AdMob user OAuth credentials: " + ", ".join(missing) + ". "
            "See android/ADMOB.md for the one-time OAuth setup."
        )
    return {key: str(values[key]).strip() for key in required}


def exchange_refresh_token(credentials: dict[str, str]) -> str:
    response = request_json(
        TOKEN_URL,
        method="POST",
        form={
            "client_id": credentials["client_id"],
            "client_secret": credentials["client_secret"],
            "refresh_token": credentials["refresh_token"],
            "grant_type": "refresh_token",
        },
    )
    token = str(response.get("access_token", "")).strip()
    if not token:
        raise RuntimeError(f"OAuth token exchange did not return access_token: {response}")
    return token


def normalize_publisher_id(value: str) -> str:
    value = value.strip()
    if value.startswith("accounts/"):
        value = value.removeprefix("accounts/")
    if not value.startswith("pub-"):
        value = f"pub-{value}"
    return value


def verify_app_ads_txt(publisher_id: str) -> None:
    expected = f"google.com, {publisher_id}, DIRECT, {APP_ADS_AUTHORITY_ID}"
    request = urllib.request.Request(APP_ADS_URL, headers={"User-Agent": "EZTrivia-AdMob-Setup/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            text = response.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Could not fetch {APP_ADS_URL}: {exc}") from exc

    lines = {line.strip() for line in text.splitlines() if line.strip() and not line.lstrip().startswith("#")}
    if expected not in lines:
        raise RuntimeError(
            f"{APP_ADS_URL} does not publish the expected AdMob seller record: {expected}"
        )
    print(f"Verified app-ads.txt publisher record for {publisher_id}.")


def list_all(token: str, url: str, field: str) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    page_token = ""
    while True:
        params = {"pageSize": "20000"}
        if page_token:
            params["pageToken"] = page_token
        payload = request_json(f"{url}?{urllib.parse.urlencode(params)}", token=token)
        items.extend(payload.get(field, []))
        page_token = str(payload.get("nextPageToken", "")).strip()
        if not page_token:
            return items


def select_existing_app(apps: list[dict[str, Any]]) -> dict[str, Any] | None:
    android = [app for app in apps if app.get("platform") == "ANDROID"]
    linked = [
        app
        for app in android
        if (app.get("linkedAppInfo") or {}).get("appStoreId") == PACKAGE_NAME
    ]
    if len(linked) > 1:
        raise RuntimeError(f"Found multiple Android AdMob apps linked to {PACKAGE_NAME}; refusing to guess.")
    if linked:
        return linked[0]

    manual = [
        app
        for app in android
        if (app.get("manualAppInfo") or {}).get("displayName") == APP_DISPLAY_NAME
    ]
    if len(manual) > 1:
        raise RuntimeError(
            f"Found multiple unlinked Android AdMob apps named {APP_DISPLAY_NAME!r}; refusing to guess."
        )
    return manual[0] if manual else None


def create_app(token: str, apps_url: str) -> dict[str, Any]:
    # Prefer the known Play package. The package/application ID is permanent and
    # already fixed by the published Internal Testing app. If AdMob cannot link
    # an app that is not publicly discoverable yet, fall back to a manual app.
    linked_payload = {
        "platform": "ANDROID",
        "linkedAppInfo": {"appStoreId": PACKAGE_NAME},
    }
    try:
        app = request_json(apps_url, method="POST", token=token, payload=linked_payload)
        print(f"Created Android AdMob app linked to Google Play package {PACKAGE_NAME}.")
        return app
    except ApiError as exc:
        if exc.status == 403:
            raise RuntimeError(
                "AdMob rejected apps.create with HTTP 403. Google documents this as a limited-access "
                "method; create the Android app in the AdMob UI (or obtain API create access), then re-run. "
                f"Response: {exc.body}"
            ) from exc
        if exc.status not in (400, 404):
            raise
        print(
            "AdMob could not create a Play-linked app yet; trying a manual Android app so Internal Testing "
            "can use production inventory. Link it to the Play listing in AdMob when Google exposes the app."
        )

    try:
        return request_json(
            apps_url,
            method="POST",
            token=token,
            payload={
                "platform": "ANDROID",
                "manualAppInfo": {"displayName": APP_DISPLAY_NAME},
            },
        )
    except ApiError as exc:
        if exc.status == 403:
            raise RuntimeError(
                "AdMob rejected apps.create with HTTP 403. Google documents this as a limited-access "
                "method; create the Android app in the AdMob UI (or obtain API create access), then re-run. "
                f"Response: {exc.body}"
            ) from exc
        raise


def select_banner(ad_units: list[dict[str, Any]], app_id: str) -> dict[str, Any] | None:
    banners = [
        unit
        for unit in ad_units
        if unit.get("appId") == app_id and unit.get("adFormat") == "BANNER"
    ]
    named = [unit for unit in banners if unit.get("displayName") == BANNER_DISPLAY_NAME]
    if len(named) == 1:
        return named[0]
    if len(named) > 1:
        raise RuntimeError(f"Found multiple banner units named {BANNER_DISPLAY_NAME!r}; refusing to guess.")
    if len(banners) == 1:
        return banners[0]
    if len(banners) > 1:
        raise RuntimeError(
            "Found multiple Android banner ad units for EZ Trivia and none has the expected display name; "
            "rename/select the intended unit in AdMob before continuing."
        )
    return None


def create_banner(token: str, ad_units_url: str, app_id: str) -> dict[str, Any]:
    try:
        return request_json(
            ad_units_url,
            method="POST",
            token=token,
            payload={
                "appId": app_id,
                "displayName": BANNER_DISPLAY_NAME,
                "adFormat": "BANNER",
                "adTypes": ["RICH_MEDIA", "VIDEO"],
            },
        )
    except ApiError as exc:
        if exc.status == 403:
            raise RuntimeError(
                "AdMob rejected adUnits.create with HTTP 403. Google documents this as a limited-access "
                "method; create one Banner unit for the Android EZ Trivia app in the AdMob UI, then re-run. "
                f"Response: {exc.body}"
            ) from exc
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--publisher-id",
        default=os.environ.get("ADMOB_PUBLISHER_ID", DEFAULT_PUBLISHER_ID),
        help=f"AdMob publisher ID (default: {DEFAULT_PUBLISHER_ID})",
    )
    parser.add_argument(
        "--create",
        action="store_true",
        help="Create the Android app/banner if missing (AdMob limited-access API methods).",
    )
    parser.add_argument("--output", type=Path, help="Write non-secret inventory/status JSON here.")
    args = parser.parse_args()

    publisher_id = normalize_publisher_id(args.publisher_id)
    verify_app_ads_txt(publisher_id)

    credentials = load_oauth_credentials()
    access_token = exchange_refresh_token(credentials)

    account = f"accounts/{publisher_id}"
    apps_url = f"{API_ROOT}/{account}/apps"
    ad_units_url = f"{API_ROOT}/{account}/adUnits"

    try:
        apps = list_all(access_token, apps_url, "apps")
    except ApiError as exc:
        if exc.status in (401, 403):
            raise RuntimeError(
                "The OAuth user cannot read this AdMob publisher inventory. Confirm the refresh token was "
                "authorized by a user with access to the publisher and includes the "
                "https://www.googleapis.com/auth/admob.monetization scope. "
                f"Response: {exc.body}"
            ) from exc
        raise

    app = select_existing_app(apps)
    if app is None:
        if not args.create:
            raise RuntimeError(
                f"No Android AdMob app was found for {PACKAGE_NAME}/{APP_DISPLAY_NAME}. Re-run with --create."
            )
        app = create_app(access_token, apps_url)

    app_id = str(app.get("appId", "")).strip()
    if not app_id.startswith(f"ca-app-{publisher_id}~"):
        raise RuntimeError(f"Unexpected AdMob app ID for publisher {publisher_id}: {app_id!r}")

    ad_units = list_all(access_token, ad_units_url, "adUnits")
    banner = select_banner(ad_units, app_id)
    if banner is None:
        if not args.create:
            raise RuntimeError(f"No banner ad unit exists for Android AdMob app {app_id}. Re-run with --create.")
        banner = create_banner(access_token, ad_units_url, app_id)

    banner_id = str(banner.get("adUnitId", "")).strip()
    if not banner_id.startswith(f"ca-app-{publisher_id}/"):
        raise RuntimeError(f"Unexpected banner ad-unit ID for publisher {publisher_id}: {banner_id!r}")

    linked_info = app.get("linkedAppInfo") or {}
    result = {
        "publisherId": publisher_id,
        "packageName": PACKAGE_NAME,
        "appId": app_id,
        "appResource": app.get("name"),
        "appApprovalState": app.get("appApprovalState"),
        "playStoreLinked": linked_info.get("appStoreId") == PACKAGE_NAME,
        "linkedStoreId": linked_info.get("appStoreId"),
        "bannerAdUnitId": banner_id,
        "bannerResource": banner.get("name"),
        "bannerDisplayName": banner.get("displayName"),
        "bannerAdTypes": banner.get("adTypes", []),
        "appAdsTxt": APP_ADS_URL,
    }

    print("Android AdMob production inventory is ready:")
    print(f"  publisher: {publisher_id}")
    print(f"  app ID: {app_id}")
    print(f"  app approval: {result['appApprovalState']}")
    print(f"  Play package linked: {result['playStoreLinked']}")
    print(f"  banner ID: {banner_id}")

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"Wrote non-secret result to {args.output}")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ApiError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
