#!/usr/bin/env python3
"""Print the current US customer price for EZ Trivia's iOS Remove Ads IAP."""

from __future__ import annotations

import os
import time
from pathlib import Path

import jwt
import requests

BASE_V1 = "https://api.appstoreconnect.apple.com/v1"
BUNDLE_ID = "com.rsm.eztrivia"
PRODUCT_ID = "com.rsm.eztrivia.removeads"


def token() -> str:
    key_id = os.environ["ASC_KEY_ID"].strip()
    issuer_id = os.environ["ASC_ISSUER_ID"].strip()
    key_path = Path(os.path.expanduser(os.environ["ASC_KEY_PATH"]))
    now = int(time.time())
    return jwt.encode(
        {"iss": issuer_id, "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"},
        key_path.read_text(),
        algorithm="ES256",
        headers={"kid": key_id},
    )


session = requests.Session()
session.headers["Authorization"] = f"Bearer {token()}"


def get(url: str, **params):
    response = session.get(url, params=params, timeout=30)
    response.raise_for_status()
    return response.json()


apps = get(f"{BASE_V1}/apps", **{"filter[bundleId]": BUNDLE_ID})["data"]
if not apps:
    raise SystemExit("EZ Trivia app not found")
app_id = apps[0]["id"]

iaps = get(f"{BASE_V1}/apps/{app_id}/inAppPurchasesV2", **{"limit": 50})["data"]
iap = next((item for item in iaps if item["attributes"].get("productId") == PRODUCT_ID), None)
if iap is None:
    raise SystemExit("Remove Ads IAP not found")
iap_id = iap["id"]

prices = get(
    f"{BASE_V1}/inAppPurchasePriceSchedules/{iap_id}/manualPrices",
    **{
        "filter[territory]": "USA",
        "include": "inAppPurchasePricePoint,territory",
        "fields[inAppPurchasePrices]": "startDate,endDate,inAppPurchasePricePoint,territory",
        "fields[inAppPurchasePricePoints]": "customerPrice,territory",
        "fields[territories]": "currency",
        "limit": 200,
    },
)

points = {
    item["id"]: item
    for item in prices.get("included", [])
    if item.get("type") == "inAppPurchasePricePoints"
}
territories = {
    item["id"]: item
    for item in prices.get("included", [])
    if item.get("type") == "territories"
}

current = next(
    (item for item in prices.get("data", []) if item.get("attributes", {}).get("startDate") is None),
    None,
)
if current is None and prices.get("data"):
    current = prices["data"][0]
if current is None:
    raise SystemExit("No US manual price is configured")

point_ref = (
    current.get("relationships", {})
    .get("inAppPurchasePricePoint", {})
    .get("data")
)
if not point_ref or point_ref["id"] not in points:
    raise SystemExit("US price point relationship was not returned")

point = points[point_ref["id"]]
customer_price = point.get("attributes", {}).get("customerPrice")
territory_ref = point.get("relationships", {}).get("territory", {}).get("data")
currency = None
if territory_ref and territory_ref["id"] in territories:
    currency = territories[territory_ref["id"]].get("attributes", {}).get("currency")

print(f"REMOVE_ADS_US_PRICE={customer_price}")
print(f"REMOVE_ADS_US_CURRENCY={currency or 'USD'}")
print(f"REMOVE_ADS_IAP_STATE={iap['attributes'].get('state')}")
