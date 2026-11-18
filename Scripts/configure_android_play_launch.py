#!/usr/bin/env python3
"""Configure the parts of the Android launch that the Play Developer API exposes.

This script intentionally does not try to submit Data Safety/content-rating forms,
enable Play Games Saved Games, or configure AdMob Privacy & messaging because
those settings are not exposed by the credentials/APIs used here.
"""

import json
import os
from urllib.parse import quote

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "com.rsm.eztrivia"
PRODUCT_ID = "com.rsm.eztrivia.removeads"
PURCHASE_OPTION_ID = "buy"
PLAY_API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
REMOVE_ADS_USD = {"currencyCode": "USD", "units": "0", "nanos": 990_000_000}

TITLE = "EZ Trivia"
SHORT_DESCRIPTION = "2,341 questions, Daily Challenge, Quick Play, and head-to-head trivia."
FULL_DESCRIPTION = """EZ Trivia makes it easy to play a quick round, test yourself in a favorite category, or settle a score with a friend.

Choose from 16 categories and three difficulty levels, or jump into Quick Play for a ten-question mix that ramps from Easy to Hard. Every answer includes a short explanation, so even a miss can teach you something.

Come back each day for the Daily Challenge and play the same ten-question set for that day. Build a streak, compare your score, and track your history.

Want a head-to-head round? Create a Friend Challenge and share the link or short code. Your friend gets the exact same questions and answer order.

Track question coverage, recent results, lifetime category points, and achievements. Google Play Games can mirror configured achievements and leaderboard scores when you are signed in. Core trivia play does not require a separate EZ Trivia account or a network connection.

EZ Trivia includes 2,341 questions across 16 categories, including history, science, sports, geography, entertainment, flags, and more.

Advertising is limited to a banner on the Play screen. A one-time Remove Ads purchase is available through Google Play."""


def fail(response, context):
    print(f"::error::{context}: HTTP {response.status_code}: {response.text[:4000]}")
    raise SystemExit(1)


def main():
    raw = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise SystemExit("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not available")

    credentials = service_account.Credentials.from_service_account_info(
        json.loads(raw),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(credentials)

    # 1. Update the editable en-US store listing. This wording deliberately does
    # not claim full Android progress sync while Saved Games is still disabled.
    edit_response = session.post(f"{PLAY_API}/applications/{PACKAGE}/edits", json={}, timeout=60)
    if edit_response.status_code != 200:
        fail(edit_response, "Could not create Play edit")
    edit_id = edit_response.json()["id"]

    listing = {
        "language": "en-US",
        "title": TITLE,
        "shortDescription": SHORT_DESCRIPTION,
        "fullDescription": FULL_DESCRIPTION,
    }
    listing_response = session.put(
        f"{PLAY_API}/applications/{PACKAGE}/edits/{edit_id}/listings/en-US",
        json=listing,
        timeout=60,
    )
    if listing_response.status_code != 200:
        fail(listing_response, "Could not update en-US store listing")

    # This account is configured for managed publishing behavior where edits are
    # sent through the review pipeline automatically; Play rejects the legacy
    # changesNotSentForReview flag, so commit with no override.
    commit_response = session.post(
        f"{PLAY_API}/applications/{PACKAGE}/edits/{edit_id}:commit",
        json={},
        timeout=60,
    )
    if commit_response.status_code != 200:
        fail(commit_response, "Could not commit Play listing edit")
    print("Store listing updated and committed.")

    # 2. Use the same US customer price as the approved iOS non-consumable
    # ($0.99). Play's conversion endpoint supplies current per-region prices and
    # the exact regionsVersion required by the one-time-products API.
    pricing_response = session.post(
        f"{PLAY_API}/applications/{PACKAGE}/pricing:convertRegionPrices",
        json={"price": REMOVE_ADS_USD},
        timeout=60,
    )
    if pricing_response.status_code != 200:
        fail(pricing_response, "Could not convert Remove Ads regional prices")
    pricing = pricing_response.json()
    regions_version = pricing["regionVersion"]["version"]

    regional_configs = []
    for key, converted in sorted(pricing.get("convertedRegionPrices", {}).items()):
        region_code = converted.get("regionCode") or key
        price = converted.get("price")
        if region_code and price:
            regional_configs.append(
                {
                    "regionCode": region_code,
                    "price": price,
                    "availability": "AVAILABLE",
                }
            )

    other = pricing["convertedOtherRegionsPrice"]
    product = {
        "packageName": PACKAGE,
        "productId": PRODUCT_ID,
        "listings": [
            {
                "languageCode": "en-US",
                "title": "Remove Ads",
                "description": "Permanently remove banner advertising from EZ Trivia.",
            }
        ],
        "taxAndComplianceSettings": {"isTokenizedDigitalAsset": False},
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

    encoded_product = quote(PRODUCT_ID, safe="")
    upsert_response = session.patch(
        f"{PLAY_API}/applications/{PACKAGE}/onetimeproducts/{encoded_product}",
        params={
            "updateMask": "listings,taxAndComplianceSettings,purchaseOptions",
            "regionsVersion.version": regions_version,
            "allowMissing": "true",
        },
        json=product,
        timeout=60,
    )
    if upsert_response.status_code != 200:
        fail(upsert_response, "Could not create/update Remove Ads one-time product")
    print(f"Remove Ads product upserted at US $0.99 using regions version {regions_version}.")

    activate_response = session.post(
        f"{PLAY_API}/applications/{PACKAGE}/oneTimeProducts/{encoded_product}/purchaseOptions:batchUpdateStates",
        json={
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
        timeout=60,
    )
    if activate_response.status_code != 200:
        fail(activate_response, "Could not activate Remove Ads purchase option")
    print("Remove Ads purchase option activated.")

    verify_response = session.get(
        f"{PLAY_API}/applications/{PACKAGE}/oneTimeProducts/{encoded_product}",
        timeout=60,
    )
    if verify_response.status_code != 200:
        fail(verify_response, "Could not verify Remove Ads product")
    verified = verify_response.json()
    option = next(
        (x for x in verified.get("purchaseOptions", []) if x.get("purchaseOptionId") == PURCHASE_OPTION_ID),
        None,
    )
    if not option or option.get("state") != "ACTIVE":
        print("::error::Remove Ads exists but the buy purchase option is not ACTIVE")
        print(json.dumps(verified, indent=2)[:8000])
        raise SystemExit(1)

    print("Verified Remove Ads purchase option state: ACTIVE")
    print(f"Verified configured regional prices: {len(option.get('regionalPricingAndAvailabilityConfigs', []))}")


if __name__ == "__main__":
    main()
