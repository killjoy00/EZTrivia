#!/usr/bin/env python3
"""Safely complete Star/Run's in-app-purchase draft: price, availability, review note."""
import json,os,urllib.parse
from pathlib import Path
from setup_starrun_iap import apple,SKU
PID="6835610167"; PRICE="2.99"; BASE="USA"
def missing404(path):
    try:return apple("GET",path)
    except SystemExit as exc:
        if "HTTP 404" in str(exc):return None
        raise
def data(path):
    r=apple("GET",path)
    return r.get("data",[])
def main():
    req=json.loads(Path(".github/starrun-iap-merchant-request.json").read_text())
    expected={"bundleID":"com.rsm.starrun","productID":SKU,"priceUSD":PRICE,"baseTerritory":BASE,"mode":"draft_only"}
    if any(req.get(k)!=v for k,v in expected.items()):raise SystemExit("Unapproved IAP pricing parameters")
    original=apple("GET",f"/v2/inAppPurchases/{PID}")["data"]
    a=original["attributes"]
    if a.get("productId")!=SKU or a.get("inAppPurchaseType")!="NON_CONSUMABLE":
        raise SystemExit("Apple target SKU or purchase type mismatch")
    print("Verified existing StoreKit non-consumable",SKU)
    note=("Remove Ads is a one-time, non-consumable purchase. On iPhone, open the main menu's Settings gear, "
      "then choose Remove Ads. Restore Purchases is directly below it. The entitlement removes banner ads "
      "from the menu and results screens; gameplay remains free. The product is confirmed against Apple "
      "StoreKit transactions, and canceled purchases don't grant ownership. Please test on an iPhone.")
    if a.get("reviewNote")!=note:
        apple("PATCH",f"/v2/inAppPurchases/{PID}",{"data":{"type":"inAppPurchases","id":PID,"attributes":{"reviewNote":note}}})
        print("Saved App Review instructions")
    else:print("App Review instructions already saved")

    # Do not replace an established price: Apple schedules are wholesale updates.
    schedule=missing404(f"/v2/inAppPurchases/{PID}/iapPriceSchedule")
    configured=False
    if schedule and schedule.get("data"):
        sid=schedule["data"]["id"]
        manual=data(f"/v1/inAppPurchasePriceSchedules/{sid}/manualPrices?limit=50")
        if manual:
            print("Existing purchase price schedule preserved. Price entries:",len(manual))
            configured=True
    if not configured:
        qs=urllib.parse.urlencode({"filter[territory]":BASE,"limit":200})
        points=data(f"/v2/inAppPurchases/{PID}/pricePoints?"+qs)
        chosen=next((p for p in points if str(p.get("attributes",{}).get("customerPrice",""))==PRICE),None)
        if chosen is None:
            raise SystemExit(f"Apple has no listed USD {PRICE} price point in first 200 tiers; no price submitted")
        priceid=chosen["id"]
        reference="starrun-remove-ads-USA-299"
        price_payload={"data":{"type":"inAppPurchasePriceSchedules","relationships":{
          "inAppPurchase":{"data":{"type":"inAppPurchases","id":PID}},
          "baseTerritory":{"data":{"type":"territories","id":BASE}},
          "manualPrices":{"data":[{"type":"inAppPurchasePrices","id":reference}]}
        }},"included":[{
          "id":reference,"type":"inAppPurchasePrices","attributes":{"startDate":None},
          "relationships":{
            "inAppPurchasePricePoint":{"data":{"type":"inAppPurchasePricePoints","id":priceid}},
            "inAppPurchaseV2":{"data":{"type":"inAppPurchases","id":PID}}
          }
        }]}
        created=apple("POST","/v1/inAppPurchasePriceSchedules",price_payload)
        print("Created Apple one-time price: USD",PRICE,"territory USA; schedule id",created["data"]["id"])
    # Select all Apple storefronts. Existing narrower selections are preserved.
    availability=missing404(f"/v2/inAppPurchases/{PID}/inAppPurchaseAvailability")
    if availability and availability.get("data"):
        print("Existing territory availability preserved id",availability["data"]["id"])
    else:
        territories=data("/v1/territories?limit=200")
        ids=sorted({str(t["id"]) for t in territories})
        if len(ids)<150 or "USA" not in ids:raise SystemExit("Apple did not provide its full supported territories; no availability changed")
        result=apple("POST","/v1/inAppPurchaseAvailabilities",{"data":{
          "type":"inAppPurchaseAvailabilities",
          "attributes":{"availableInNewTerritories":True},
          "relationships":{
            "inAppPurchase":{"data":{"type":"inAppPurchases","id":PID}},
            "availableTerritories":{"data":[{"type":"territories","id":x} for x in ids]}
          }
        }})
        print("Configured IAP availability in",len(ids),"Apple territories (only once reviewed and active)")
    latest=apple("GET",f"/v2/inAppPurchases/{PID}")["data"]
    if latest["attributes"].get("reviewNote")!=note:raise SystemExit("Review note failed readback")
    shot=missing404(f"/v2/inAppPurchases/{PID}/appStoreReviewScreenshot")
    screenshot=shot.get("data",{}).get("attributes",{}).get("assetDeliveryState",{}).get("state","MISSING") if shot else "MISSING"
    print("FINAL IAP CHECK",SKU,"screenshot",screenshot,"price target USD",PRICE)
    print("Apple has NOT received any App Review submission or public launch request")
    with Path(os.environ.get("GITHUB_STEP_SUMMARY","/dev/null")).open("a") as f:
        f.write(f"Star/Run Remove Ads: Apple SKU {SKU}; USD {PRICE}; review screenshot {screenshot}. "
                "Draft only; no submission or release.\n")
if __name__=="__main__":main()
