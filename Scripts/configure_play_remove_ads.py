#!/usr/bin/env python3
"""Create and activate EZ Trivia's Android Remove Ads product if it is absent.

Create-only by design: if the product already exists, report its current state
and make no changes. The US base price mirrors the approved iOS IAP at $0.99.
"""

from __future__ import annotations

import json
import os
import sys

import requests

PACKAGE = "com.rsm.eztrivia"
PRODUCT_ID = "com.rsm.eztrivia.removeads"
PURCHASE_OPTION_ID = "buy"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3"

TOKEN = os.environ["ACCESS_TOKEN"]
session = requests.Session()
session.headers.update({
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json",
})


def request(method: str, url: str, *, payload=None, expected=(200,)):
    response = session.request(method, url, json=payload, timeout=60)
    if response.status_code not in expected:
        raise RuntimeError(
            f"{method} {url} -> HTTP {response.status_code}: {response.text[:2000]}"
        )
    if not response.content:
        return None
    return response.json()


def summarize(product: dict) -> None:
    listing = next(
        (x for x in product.get("listings", []) if x.get("languageCode") == "en-US"),
        {},
    )
    options = product.get("purchaseOptions", [])
    option = next(
        (x for x in options if x.get("purchaseOptionId") == PURCHASE_OPTION_ID),
        options[0] if options else {},
    )
    us = next(
        (
            x for x in option.get("regionalPricingAndAvailabilityConfigs", [])
            if x.get("regionCode") == "US"
        ),
        {},
    )
    print(f"PLAY_REMOVE_ADS_PRODUCT={product.get('productId')}")
    print(f"PLAY_REMOVE_ADS_TITLE={listing.get('title')}")
    print(f"PLAY_REMOVE_ADS_OPTION={option.get('purchaseOptionId')}")
    print(f"PLAY_REMOVE_ADS_STATE={option.get('state')}")
    print(f"PLAY_REMOVE_ADS_US_PRICE={json.dumps(us.get('price'), sort_keys=True)}")
    print(f"PLAY_REMOVE_ADS_US_AVAILABILITY={us.get('availability')}")


product_url = f"{BASE}/applications/{PACKAGE}/oneTimeProducts/{PRODUCT_ID}"
existing_response = session.get(product_url, timeout=60)
if existing_response.status_code == 200:
    print("Remove Ads already exists; leaving Google Play configuration unchanged.")
    summarize(existing_response.json())
    sys.exit(0)
if existing_response.status_code != 404:
    raise RuntimeError(
        f"GET existing product -> HTTP {existing_response.status_code}: "
        f"{existing_response.text[:2000]}"
    )

print("Remove Ads does not exist yet; creating it at the approved iOS base price of $0.99 USD.")

conversion = request(
    "POST",
    f"{BASE}/applications/{PACKAGE}/pricing:convertRegionPrices",
    payload={
        "price": {
            "currencyCode": "USD",
            "units": "0",
            "nanos": 990_000_000,
        }
    },
)

region_version = conversion["regionVersion"]
regional_configs = [
    {
        "regionCode": region_code,
        "price": converted["price"],
        "availability": "AVAILABLE",
    }
    for region_code, converted in sorted(conversion["convertedRegionPrices"].items())
]
other = conversion["convertedOtherRegionsPrice"]

product = {
    "packageName": PACKAGE,
    "productId": PRODUCT_ID,
    "listings": [
        {
            "languageCode": "en-US",
            "title": "Remove Ads",
            "description": "Remove banner ads from EZ Trivia with a one-time purchase.",
        }
    ],
    "purchaseOptions": [
        {
            "purchaseOptionId": PURCHASE_OPTION_ID,
            "regionalPricingAndAvailabilityConfigs": regional_configs,
            "newRegionsConfig": {
                "usdPrice": other["usdPrice"],
                "eurPrice": other["eurPrice"],
                "availability": "AVAILABLE",
            },
            "buyOption": {
                "legacyCompatible": True,
                "multiQuantityEnabled": False,
            },
        }
    ],
}

created = request(
    "POST",
    f"{BASE}/applications/{PACKAGE}/oneTimeProducts:batchUpdate",
    payload={
        "requests": [
            {
                "oneTimeProduct": product,
                "updateMask": "listings,purchaseOptions",
                "regionsVersion": region_version,
                "allowMissing": True,
            }
        ]
    },
)
created_product = created["oneTimeProducts"][0]
print("Created draft Google Play one-time product.")
summarize(created_product)

activated = request(
    "POST",
    f"{BASE}/applications/{PACKAGE}/oneTimeProducts/{PRODUCT_ID}/purchaseOptions:batchUpdateStates",
    payload={
        "requests": [
            {
                "activatePurchaseOptionRequest": {
                    "packageName": PACKAGE,
                    "productId": PRODUCT_ID,
                    "purchaseOptionId": PURCHASE_OPTION_ID,
                }
            }
        ]
    },
)
print("Activated Google Play buy option.")
summarize(activated["oneTimeProducts"][0])
