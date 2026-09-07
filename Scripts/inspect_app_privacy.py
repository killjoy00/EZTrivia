#!/usr/bin/env python3
"""Read EZ Trivia's age rating and App Privacy answers from App Store Connect.

Read-only on purpose. The privacy nutrition label and the age rating are
declarations about the app's behavior with real consequences for getting them
wrong, so this reports what is currently on file and leaves changing it to a
deliberate, separate act.

Run through the same App Store Connect API key the release workflows use.
"""

from __future__ import annotations

import json
import os
import sys
import time
from pathlib import Path

import jwt
import requests

BASE_URL = "https://api.appstoreconnect.apple.com/v1"
BUNDLE_ID = "com.rsm.eztrivia"


def api_token() -> str:
    key_id = os.environ["ASC_KEY_ID"].strip()
    issuer_id = os.environ["ASC_ISSUER_ID"].strip()
    key_path = Path(os.path.expanduser(
        os.environ.get("ASC_KEY_PATH", f"~/private_keys/AuthKey_{key_id}.p8")
    ))
    now = int(time.time())
    return jwt.encode(
        {"iss": issuer_id, "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"},
        key_path.read_text(),
        algorithm="ES256",
        headers={"kid": key_id},
    )


session = requests.Session()
session.headers.update({"Authorization": f"Bearer {api_token()}"})


def get(path: str, **params):
    response = session.get(f"{BASE_URL}{path}", params=params, timeout=30)
    if response.status_code >= 300:
        print(f"  ! GET {path} -> HTTP {response.status_code}: {response.text[:300]}")
        return None
    return response.json()


def heading(title: str) -> None:
    print(f"\n{title}\n{'-' * len(title)}")


apps = get("/apps", **{"filter[bundleId]": BUNDLE_ID})
if not apps or not apps.get("data"):
    sys.exit(f"No app found for bundle id {BUNDLE_ID}")
app = apps["data"][0]
app_id = app["id"]
print(f"{app['attributes'].get('name')}  ({BUNDLE_ID})  app id {app_id}")

heading("App Store versions")
versions = get(f"/apps/{app_id}/appStoreVersions", **{"limit": 5}) or {"data": []}
for version in versions["data"]:
    attrs = version["attributes"]
    print(f"  {attrs.get('versionString'):<10} {attrs.get('appStoreState')}  "
          f"platform={attrs.get('platform')}  id={version['id']}")

target = versions["data"][0] if versions["data"] else None

heading("Relationships this API key can actually reach")
# The endpoint names for age rating and the privacy label have moved between
# API versions, so rather than guessing paths, ask the resource what it has.
detail = get(f"/apps/{app_id}")
if detail and detail.get("data"):
    rels = sorted(detail["data"].get("relationships", {}).keys())
    print(f"  app: {', '.join(rels)}")
if target:
    vdetail = get(f"/appStoreVersions/{target['id']}")
    if vdetail and vdetail.get("data"):
        vrels = sorted(vdetail["data"].get("relationships", {}).keys())
        print(f"  appStoreVersion: {', '.join(vrels)}")

heading("Age rating declaration")
declaration = None
infos = get(f"/apps/{app_id}/appInfos", **{"limit": 5}) or {"data": []}
for info in infos["data"]:
    state = info["attributes"].get("appStoreState") or info["attributes"].get("state")
    print(f"  appInfo {info['id']}  state={state}")
    found = get(f"/appInfos/{info['id']}/ageRatingDeclaration")
    if found and found.get("data"):
        declaration = found
        break
if declaration and declaration.get("data"):
    attrs = declaration["data"]["attributes"]
    nonzero = {k: v for k, v in sorted(attrs.items())
               if v not in (None, False, "NONE", "NO", 0)}
    print(f"  {len(attrs)} fields on file; {len(nonzero)} are non-default:")
    for key, value in nonzero.items():
        print(f"    {key} = {value}")
    if not nonzero:
        print("    every field is at its default (no mature content declared)")
else:
    print("  (no age rating declaration returned)")

heading("In-app purchases")
# StoreKit only returns a product once App Store Connect considers it complete.
# A product sitting in MISSING_METADATA is invisible to Product.products(for:),
# which is indistinguishable in the app from "no such product".
iaps = get(f"/apps/{app_id}/inAppPurchasesV2", **{"limit": 50})
if iaps is None:
    print("  (endpoint unavailable to this key)")
elif not iaps.get("data"):
    print("  none defined for this app")
else:
    for iap in iaps["data"]:
        attrs = iap["attributes"]
        print(f"  {attrs.get('productId')}")
        print(f"      name:  {attrs.get('name')}")
        print(f"      type:  {attrs.get('inAppPurchaseType')}")
        print(f"      state: {attrs.get('state')}")
        ready = attrs.get("state") in {"READY_TO_SUBMIT", "APPROVED", "WAITING_FOR_REVIEW",
                                       "IN_REVIEW", "PENDING_BINARY_APPROVAL"}
        print(f"      -> {'visible to StoreKit' if ready else 'NOT returned by StoreKit in this state'}")

def get_v2(path: str, **params):
    """The in-app purchase resources live under /v2, unlike everything else here."""
    response = session.get(
        f"https://api.appstoreconnect.apple.com/v2{path}", params=params, timeout=30
    )
    if response.status_code >= 300:
        print(f"  ! GET /v2{path} -> HTTP {response.status_code}: {response.text[:200]}")
        return None
    return response.json()


heading("In-app purchase readiness detail")
# READY_TO_SUBMIT only says the metadata form is complete. A product can still
# be unfetchable by Product.products(for:) because it has no price schedule or
# no available territories, and neither shows up in `state`.
if iaps and iaps.get("data"):
    for iap in iaps["data"]:
        iap_id = iap["id"]
        print(f"  {iap['attributes'].get('productId')}  (id={iap_id})")

        schedule = get_v2(f"/inAppPurchases/{iap_id}/iapPriceSchedule",
                          include="manualPrices", **{"limit[manualPrices]": 20})
        if schedule is None:
            print("      price schedule: (unreadable)")
        elif not schedule.get("data"):
            print("      price schedule: NONE -> StoreKit cannot return this product")
        else:
            prices = [i for i in schedule.get("included", []) if i["type"] == "inAppPurchasePrices"]
            print(f"      price schedule: present, {len(prices)} manual price rows")
            for price in prices[:3]:
                attrs = price.get("attributes", {})
                print(f"          startDate={attrs.get('startDate')} endDate={attrs.get('endDate')}")

        availability = get_v2(f"/inAppPurchases/{iap_id}/inAppPurchaseAvailability",
                              include="availableTerritories",
                              **{"limit[availableTerritories]": 200})
        if availability is None:
            print("      availability: (unreadable)")
        elif not availability.get("data"):
            print("      availability: NONE -> not for sale in any territory")
        else:
            territories = [i for i in availability.get("included", []) if i["type"] == "territories"]
            attrs = availability["data"].get("attributes", {})
            print(f"      availability: availableInNewTerritories={attrs.get('availableInNewTerritories')}, "
                  f"{len(territories)} territories")

        localizations = get_v2(f"/inAppPurchases/{iap_id}/inAppPurchaseLocalizations", **{"limit": 20})
        if localizations and localizations.get("data"):
            for loc in localizations["data"]:
                a = loc["attributes"]
                print(f"      localization: {a.get('locale')} name={a.get('name')!r} state={a.get('state')}")
        else:
            print("      localizations: NONE -> incomplete listing")

        shot = get_v2(f"/inAppPurchases/{iap_id}/appStoreReviewScreenshot")
        if shot and shot.get("data"):
            a = shot["data"].get("attributes", {})
            print(f"      review screenshot: {a.get('fileName')} state={(a.get('assetDeliveryState') or {}).get('state')}")
        else:
            print("      review screenshot: NONE (required before an IAP can be reviewed)")


heading("App Privacy — declared data usages")
usages = get(f"/apps/{app_id}/appDataUsages",
             include="category,grouping,purpose,dataProtection",
             **{"limit": 200})
if usages is None:
    print("  (endpoint unavailable to this key)")
else:
    included = {(item["type"], item["id"]): item for item in usages.get("included", [])}

    def label(rel):
        data = (rel or {}).get("data")
        if not data:
            return None
        item = included.get((data["type"], data["id"]))
        return (item or {}).get("id") or data["id"]

    rows = []
    for usage in usages.get("data", []):
        rel = usage.get("relationships", {})
        rows.append({
            "category": label(rel.get("category")),
            "purpose": label(rel.get("purpose")),
            "protection": label(rel.get("dataProtection")),
        })
    if not rows:
        print("  nothing declared (label reads 'No Data Collected')")
    for row in sorted(rows, key=lambda r: (str(r["category"]), str(r["purpose"]))):
        print(f"    {str(row['category']):<34} purpose={str(row['purpose']):<28} {row['protection']}")

    state = get(f"/apps/{app_id}/appDataUsagesPublishState")
    if state and state.get("data"):
        print(f"\n  published: {state['data']['attributes'].get('published')}")
