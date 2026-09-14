#!/usr/bin/env python3
import json
import os

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

raw = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip()
if not raw:
    raise SystemExit("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not available")

info = json.loads(raw)
for scope in (
    "https://www.googleapis.com/auth/admob.readonly",
    "https://www.googleapis.com/auth/admob.monetization",
):
    creds = service_account.Credentials.from_service_account_info(info, scopes=[scope])
    session = AuthorizedSession(creds)
    response = session.get("https://admob.googleapis.com/v1/accounts", timeout=60)
    print(f"scope={scope} accounts.list -> HTTP {response.status_code}")
    print(response.text[:8000])
