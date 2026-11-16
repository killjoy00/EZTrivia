#!/usr/bin/env python3
"""Price only: one-time Ultimate Tic/Tac/Toe Remove Ads at US $1.99.

Use the existing EZTrivia Apple API key, never create another key or change
the app's own free price. Leave all existing schedules untouched unless
verified exactly equal to the approved price.
"""
import base64
import json
import os
from pathlib import Path
from urllib.parse import urlencode

from setup_starrun_iap import apple

BUNDLE = "com.killjoy00.ultttt"
SKU = "com.killjoy00.ultttt.remove_ads"
PRICE_USD = "1.99"
BASE_TERRITORY = "USA"


def get_data(path):
    return apple("GET", path).get("data")


def get_optional(path):
    try:
        return apple("GET", path)
    except SystemExit as exc:
        if "HTTP 404" in str(exc):
            return None
        raise


def normalize_shared_key():
    """Use the normalization proven by Ultttt's successful Apple preflight."""
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
        compact = "".join(raw.split())
        raw = ("-----BEGIN PRIVATE KEY-----\n"
               + "\n".join(compact[i:i+64] for i in range(0, len(compact), 64))
               + "\n-----END PRIVATE KEY-----\n")
    os.environ["ASC_KEY_P8"] = raw


def main():
    normalize_shared_key()
    r = json.loads(Path(".github/ultttt-release-request.json").read_text())
    expected = {
        "repository": "killjoy00/Ultttt",
        "bundleID": BUNDLE,
        "appName": "Ultimate Tic/Tac/Toe",
        "mode": "price",
        "priceUSD": PRICE_USD,
        "baseTerritory": BASE_TERRITORY,
    }
    if any(r.get(key) != value for key, value in expected.items()):
        raise SystemExit("Price request does not match approved app/product/amount.")
    if r.get("version") != "1.0":
        raise SystemExit("Unexpected App Store initial version")

    apps = apple("GET", "/v1/apps", params={
        "filter[bundleId]": BUNDLE, "limit": 5
    })["data"]
    if len(apps) != 1 or apps[0]["attributes"].get("bundleId") != BUNDLE:
        raise SystemExit("Could not uniquely identify the correct Apple app")
    app_id = apps[0]["id"]
    products = apple("GET", f"/v1/apps/{app_id}/inAppPurchasesV2",
                     params={"filter[productId]": SKU, "limit": 10})["data"]
    if len(products) != 1:
        raise SystemExit(f"Expected one existing Remove Ads purchase; found {len(products)}")
    product = products[0]
    attributes = product["attributes"]
    if attributes.get("productId") != SKU or attributes.get("inAppPurchaseType") != "NON_CONSUMABLE":
        raise SystemExit("Apple product ID or purchase type is not the approved one")
    product_id = product["id"]
    print("Verified:", BUNDLE, SKU, "Apple IAP ID", product_id)

    # Apple price schedules replace existing schedule definitions. They are
    # never overwritten by this workflow: preexisting prices are read only.
    schedule = get_optional(f"/v2/inAppPurchases/{product_id}/iapPriceSchedule")
    if schedule and schedule.get("data"):
        print("Found existing Apple pricing schedule; checking rather than replacing")
    else:
        qs = urlencode({"filter[territory]": BASE_TERRITORY, "limit": 200})
        points = get_data(f"/v2/inAppPurchases/{product_id}/pricePoints?{qs}")
        chosen = next((
            point for point in points
            if str(point.get("attributes", {}).get("customerPrice", "")) == PRICE_USD
        ), None)
        if not chosen:
            raise SystemExit(f"Apple did not list a USA price point of USD {PRICE_USD}; no changes")
        point_id = chosen["id"]
        temp_id = "${ultttt-remove-ads-usa-usd199}"
        payload = {
            "data": {
                "type": "inAppPurchasePriceSchedules",
                "relationships": {
                    "inAppPurchase": {
                        "data": {"type": "inAppPurchases", "id": product_id}
                    },
                    "baseTerritory": {
                        "data": {"type": "territories", "id": BASE_TERRITORY}
                    },
                    "manualPrices": {
                        "data": [{"type": "inAppPurchasePrices", "id": temp_id}]
                    },
                },
            },
            "included": [
                {
                    "id": temp_id,
                    "type": "inAppPurchasePrices",
                    "attributes": {"startDate": None},
                    "relationships": {
                        "inAppPurchasePricePoint": {
                            "data": {"type": "inAppPurchasePricePoints", "id": point_id}
                        },
                        "inAppPurchaseV2": {
                            "data": {"type": "inAppPurchases", "id": product_id}
                        },
                    },
                }
            ],
        }
        created = apple("POST", "/v1/inAppPurchasePriceSchedules", payload)
        print("Created Apple price schedule:", created["data"]["id"])

    # Read back Apple's canonical schedule, territory and customer price.
    latest = get_optional(f"/v2/inAppPurchases/{product_id}/iapPriceSchedule")
    if not latest or not latest.get("data"):
        raise SystemExit("Apple did not return a price schedule after the operation")
    schedule = latest["data"]
    sid = schedule["id"]
    territory = (schedule.get("relationships", {})
                 .get("baseTerritory", {}).get("data") or {}).get("id")
    if territory and territory != BASE_TERRITORY:
        raise SystemExit(f"Existing base territory was {territory}, not USA: no overwrite attempted")

    details = apple("GET",
                    f"/v1/inAppPurchasePriceSchedules/{sid}/manualPrices"
                    "?include=inAppPurchasePricePoint,territory&limit=50")
    price_points = {
        p["id"]: str(p.get("attributes", {}).get("customerPrice"))
        for p in details.get("included", [])
        if p.get("type") == "inAppPurchasePricePoints"
    }
    prices = []
    for entry in details.get("data", []):
        relationships = entry.get("relationships", {})
        point = (relationships.get("inAppPurchasePricePoint", {}).get("data") or {})
        region = (relationships.get("territory", {}).get("data") or {})
        amount = price_points.get(point.get("id"))
        prices.append((region.get("id"), amount))
    print("Apple manual price entries:", prices)
    if (BASE_TERRITORY, PRICE_USD) not in prices:
        raise SystemExit("Apple read-back did not verify USD $1.99 in USA")
    print("SUCCESS: Apple confirms one-time Remove Ads = USD $1.99 in USA")
    print("Apple auto-generates corresponding non-USA prices from the base market.")
    print("No public App Review submission or app price changes were made.")
    with Path(os.environ.get("GITHUB_STEP_SUMMARY", "/dev/null")).open("a") as out:
        out.write("### Ultimate Tic/Tac/Toe Remove Ads\n")
        out.write(f"- Apple product ID: \x60{SKU}\x60\n")
        out.write("- US customer price: **$1.99**, verified via Apple's pricing API\n")
        out.write("- Non-consumable, base territory USA\n")
        out.write("- No public release or App Review submission\n")


if __name__ == "__main__":
    main()
