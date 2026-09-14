#!/usr/bin/env python3
"""Fallback creation of the Remove Ads managed product using the legacy
inappproducts endpoint. Google still supports this endpoint for managed
one-time products; it is deprecated only for subscriptions.
"""

import json
import os
from urllib.parse import quote

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "com.rsm.eztrivia"
SKU = "com.rsm.eztrivia.removeads"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3"


def main():
    raw = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise SystemExit("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not available")
    creds = service_account.Credentials.from_service_account_info(
        json.loads(raw),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(creds)

    encoded = quote(SKU, safe="")
    existing = session.get(
        f"{BASE}/applications/{PACKAGE}/inappproducts/{encoded}",
        timeout=60,
    )
    if existing.status_code == 200:
        data = existing.json()
        print("Remove Ads already exists through legacy managed-product API.")
        print(json.dumps(data, indent=2)[:8000])
        if data.get("status") != "active":
            raise SystemExit("Existing Remove Ads product is not active")
        return
    if existing.status_code != 404:
        print(existing.text[:4000])
        raise SystemExit(f"Could not check existing product: HTTP {existing.status_code}")

    body = {
        "packageName": PACKAGE,
        "sku": SKU,
        "status": "active",
        "purchaseType": "managedUser",
        "defaultPrice": {
            "priceMicros": "990000",
            "currency": "USD",
        },
        "listings": {
            "en-US": {
                "title": "Remove Ads",
                "description": "Permanently remove banner advertising from EZ Trivia.",
            }
        },
        "defaultLanguage": "en-US",
    }
    created = session.post(
        f"{BASE}/applications/{PACKAGE}/inappproducts",
        params={"autoConvertMissingPrices": "true"},
        json=body,
        timeout=60,
    )
    print(f"Create legacy managed product -> HTTP {created.status_code}")
    if created.status_code not in (200, 201):
        print(created.text[:4000])
        raise SystemExit(1)

    data = created.json()
    print(json.dumps(data, indent=2)[:8000])
    if data.get("sku") != SKU or data.get("status") != "active":
        raise SystemExit("Create returned an unexpected product state")
    print("Verified Remove Ads managed product is active at base USD $0.99.")


if __name__ == "__main__":
    main()
