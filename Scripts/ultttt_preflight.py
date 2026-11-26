#!/usr/bin/env python3
"""Read-only Apple record audit using EZTrivia's existing API credentials."""
from __future__ import annotations
import base64
import json
import os
import pathlib
import time
import jwt
import requests

BUNDLE = "com.killjoy00.ultttt"
REQUEST_PATH = pathlib.Path(".github/ultttt-release-request.json")
request = json.loads(REQUEST_PATH.read_text())
if request["bundleID"] != BUNDLE or request["repository"] != "killjoy00/Ultttt":
    raise SystemExit("Wrong app or source")
raw = os.environ["ASC_KEY_P8"].replace("\r", "").strip()
if "BEGIN PRIVATE KEY" not in raw:
    if "\\n" in raw:
        raw = raw.replace("\\n", "\n")
    else:
        try:
            decoded = base64.b64decode(raw).decode()
            if "BEGIN PRIVATE KEY" in decoded:
                raw = decoded
        except (ValueError, UnicodeDecodeError):
            pass
if "BEGIN PRIVATE KEY" not in raw:
    stripped = "".join(raw.split())
    raw = ("-----BEGIN PRIVATE KEY-----\n"
           + "\n".join(stripped[x:x+64] for x in range(0, len(stripped), 64))
           + "\n-----END PRIVATE KEY-----\n")

now = int(time.time())
token = jwt.encode(
    {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"},
    raw, algorithm="ES256", headers={"kid": os.environ["ASC_KEY_ID"]}
)
s = requests.Session()
s.headers["Authorization"] = "Bearer " + token
base = "https://api.appstoreconnect.apple.com/v1"

def api(path: str, params=None, required=False):
    try:
        res = s.get(base + path, params=params, timeout=30)
        if res.status_code != 200:
            message = "; ".join(
                (str(e.get("code", "")) + " " + str(e.get("title", "")))
                for e in res.json().get("errors", [])[:2]
            )
            if required:
                raise RuntimeError(f"{path}: HTTP {res.status_code} {message}")
            print(f"  {path}: inaccessible (HTTP {res.status_code})")
            return None
        return res.json().get("data")
    except (requests.RequestException, ValueError):
        if required:
            raise
        print(f"  {path}: unavailable")
        return None

apps = api("/apps", {"filter[bundleId]": BUNDLE, "limit": 5}, required=True) or []
if len(apps) != 1:
    raise SystemExit(f"Expected exactly 1 Apple app for {BUNDLE}, found {len(apps)}")
app = apps[0]
app_id = app["id"]
print("App record confirmed:", app["attributes"].get("name"), "(ID", app_id + ")")
print("Bundle ID:", app["attributes"].get("bundleId"))
infos = api(f"/apps/{app_id}/appInfos") or []
for info in infos[:2]:
    localizations = api(f"/appInfos/{info['id']}/appInfoLocalizations") or []
    for item in localizations:
        if item.get("attributes", {}).get("locale") == "en-US":
            attrs = item["attributes"]
            print("en-US listing name:", attrs.get("name"))
            print("subtitle:", attrs.get("subtitle"))
            print("privacy policy present:", bool(attrs.get("privacyPolicyUrl")))
versions = api(f"/apps/{app_id}/appStoreVersions", {"limit": 20}) or []
print("iOS versions:", [
    (v["attributes"].get("versionString"), v["attributes"].get("appStoreState"))
    for v in versions if v["attributes"].get("platform") == "IOS"
])
detail = api(f"/apps/{app_id}/gameCenterDetail")
print("Game Center detail:", "configured" if detail else "missing")
if detail:
    leaders = api(f"/gameCenterDetails/{detail['id']}/gameCenterLeaderboards") or []
    achievements = api(f"/gameCenterDetails/{detail['id']}/gameCenterAchievements") or []
    print("Leaderboards:", [x["attributes"].get("vendorIdentifier") for x in leaders])
    print("Achievements:", [x["attributes"].get("vendorIdentifier") for x in achievements])
items = api(f"/apps/{app_id}/inAppPurchasesV2", {"limit": 30}) or []
print("In-App Purchases:", [
    (x["attributes"].get("productId"), x["attributes"].get("state")) for x in items
])
bundles = api("/bundleIds", {"filter[identifier]": BUNDLE, "limit": 5}) or []
if bundles:
    caps = api(f"/bundleIds/{bundles[0]['id']}/bundleIdCapabilities") or []
    print("App ID capabilities:", [x["attributes"].get("capabilityType") for x in caps])
builds = api("/builds", {"filter[app]": app_id, "sort": "-uploadedDate", "limit": 5}) or []
print("TestFlight builds:", [
    (x["attributes"].get("version"), x["attributes"].get("processingState")) for x in builds
])
print("Read-only Apple preflight completed. No app data was changed.")
