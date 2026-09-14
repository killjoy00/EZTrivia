#!/usr/bin/env python3
import json
import os
import time

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PROJECT_NUMBER = "406580555223"
SERVICE = "admob.googleapis.com"

raw = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip()
if not raw:
    raise SystemExit("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not available")

info = json.loads(raw)
creds = service_account.Credentials.from_service_account_info(
    info,
    scopes=["https://www.googleapis.com/auth/cloud-platform"],
)
session = AuthorizedSession(creds)
url = f"https://serviceusage.googleapis.com/v1/projects/{PROJECT_NUMBER}/services/{SERVICE}:enable"
response = session.post(url, json={}, timeout=60)
print(f"enable {SERVICE} -> HTTP {response.status_code}")
print(response.text[:8000])
if response.status_code not in (200, 201):
    raise SystemExit(1)

operation = response.json().get("name")
if operation:
    for _ in range(24):
        poll = session.get(f"https://serviceusage.googleapis.com/v1/{operation}", timeout=60)
        print(f"poll -> HTTP {poll.status_code}: {poll.text[:2000]}")
        if poll.status_code != 200:
            raise SystemExit(1)
        data = poll.json()
        if data.get("done"):
            if data.get("error"):
                raise SystemExit("Service enable operation failed")
            break
        time.sleep(5)

check = session.get(
    f"https://serviceusage.googleapis.com/v1/projects/{PROJECT_NUMBER}/services/{SERVICE}",
    timeout=60,
)
print(f"check -> HTTP {check.status_code}: {check.text[:4000]}")
if check.status_code != 200 or check.json().get("state") != "ENABLED":
    raise SystemExit("AdMob API is not enabled")
print("AdMob API enabled.")
