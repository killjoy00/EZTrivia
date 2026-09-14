#!/usr/bin/env python3
import json
import os

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

ACCOUNT = "accounts/pub-1217971050094766"
BASE = "https://admob.googleapis.com/v1beta"
DISPLAY_NAME = "EZ Trivia"
BANNER_NAME = "Play Banner"

raw = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip()
if not raw:
    raise SystemExit("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not available")

creds = service_account.Credentials.from_service_account_info(
    json.loads(raw),
    scopes=["https://www.googleapis.com/auth/admob.monetization"],
)
session = AuthorizedSession(creds)


def require(response, context):
    print(f"{context} -> HTTP {response.status_code}")
    if response.status_code >= 300:
        print(response.text[:8000])
        raise SystemExit(1)
    return response.json()

apps = require(session.get(f"{BASE}/{ACCOUNT}/apps", timeout=60), "List AdMob apps").get("apps", [])
android = None
for app in apps:
    if app.get("platform") != "ANDROID":
        continue
    manual_name = app.get("manualAppInfo", {}).get("displayName")
    store_id = app.get("linkedAppInfo", {}).get("appStoreId")
    if manual_name == DISPLAY_NAME or store_id == "com.rsm.eztrivia":
        android = app
        break

if android is None:
    android = require(
        session.post(
            f"{BASE}/{ACCOUNT}/apps",
            json={"platform": "ANDROID", "manualAppInfo": {"displayName": DISPLAY_NAME}},
            timeout=60,
        ),
        "Create Android AdMob app",
    )
else:
    print("Reusing existing Android AdMob app.")

app_id = android["appId"]
print(f"ANDROID_ADMOB_APP_ID={app_id}")

units = require(session.get(f"{BASE}/{ACCOUNT}/adUnits", timeout=60), "List AdMob ad units").get("adUnits", [])
banner = next(
    (
        unit for unit in units
        if unit.get("appId") == app_id
        and unit.get("adFormat") == "BANNER"
        and unit.get("displayName") == BANNER_NAME
    ),
    None,
)
if banner is None:
    banner = require(
        session.post(
            f"{BASE}/{ACCOUNT}/adUnits",
            json={
                "appId": app_id,
                "displayName": BANNER_NAME,
                "adFormat": "BANNER",
                "adTypes": ["RICH_MEDIA"],
            },
            timeout=60,
        ),
        "Create Android banner ad unit",
    )
else:
    print("Reusing existing Android banner ad unit.")

print(f"ANDROID_ADMOB_BANNER_ID={banner['adUnitId']}")
print("AdMob Android app and Play banner are configured.")
