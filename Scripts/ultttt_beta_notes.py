#!/usr/bin/env python3
"""Finish Ultttt TestFlight English metadata and Remove Ads review notes.

No external TestFlight distribution, new tester invitation, app pricing,
App Review submission, or customer-facing app publication is performed.
"""
from __future__ import annotations
import base64
import json
import os
import time
import requests
import jwt

APP_ID="6835609349"
BUNDLE="com.killjoy00.ultttt"
NAME="Ultimate Tic/Tac/Toe"
IAP="com.killjoy00.ultttt.remove_ads"
BUILD="100006"
BASE="https://api.appstoreconnect.apple.com"
key=os.environ["ASC_KEY_P8"].replace("\r","").strip()
if "BEGIN PRIVATE KEY" not in key:
    if "\\n" in key:key=key.replace("\\n","\n")
    else:
        try:
            decoded=base64.b64decode(key).decode()
            if "BEGIN PRIVATE KEY" in decoded:key=decoded
        except (ValueError,UnicodeDecodeError):pass
if "BEGIN PRIVATE KEY" not in key:
    flat="".join(key.split())
    key=("-----BEGIN PRIVATE KEY-----\n"
         +"\n".join(flat[i:i+64] for i in range(0,len(flat),64))
         +"\n-----END PRIVATE KEY-----\n")
now=int(time.time())
token=jwt.encode({"iss":os.environ["ASC_ISSUER_ID"],"iat":now,"exp":now+1100,"aud":"appstoreconnect-v1"},
                 key,algorithm="ES256",headers={"kid":os.environ["ASC_KEY_ID"]})
session=requests.Session()
session.headers.update({"Authorization":"Bearer "+token,"Content-Type":"application/json","Accept":"application/json"})
def request(method,path,body=None,params=None):
    r=session.request(method,BASE+path,json=body,params=params,timeout=45)
    if not 200<=r.status_code<300:
        try:
            detail="; ".join(str(e.get("code"))+" "+str(e.get("title"))+" "+str(e.get("detail","")) for e in r.json().get("errors",[])[:2])
        except (ValueError,TypeError):
            detail=""
        raise RuntimeError(f"Apple {method} {path} HTTP {r.status_code}: {detail[:290]}")
    return r.json() if r.content else {}
def data(path,params=None):
    return request("GET",path,params=params).get("data")
def listall(path,params=None):
    items=[]
    while path:
        r=request("GET",path,params=params)
        items.extend(r.get("data",[]))
        path=r.get("links",{}).get("next")
        params=None
    return items
def ref(kind,id):
    return {"data":{"type":kind,"id":id}}

apps=data("/v1/apps",{"filter[bundleId]":BUNDLE,"limit":5})
if len(apps)!=1 or apps[0]["id"]!=APP_ID or apps[0]["attributes"].get("name")!=NAME:
    raise SystemExit("App identity mismatch; no Apple changes")
print("Verified exact Apple app",NAME,BUNDLE,APP_ID)
failures=[]
def stage(name,fn):
    print("\n==",name,"==")
    try:fn()
    except Exception as exc:
        failures.append(name)
        print("::error::"+name+": "+str(exc))

def testflight_metadata():
    builds=data("/v1/builds",{"filter[app]":APP_ID,"sort":"-uploadedDate","limit":40})
    build=next((x for x in builds if x["attributes"].get("version")==BUILD),None)
    if not build:
        raise RuntimeError("No registered TestFlight build "+BUILD)
    if build["attributes"].get("processingState")!="VALID":
        raise RuntimeError("TestFlight build is not VALID; refusing to set beta metadata")
    bid=build["id"]
    print("Confirmed VALID iOS build",BUILD,"Apple build ID",bid)
    current=data(f"/v1/builds/{bid}/betaBuildLocalizations",{"limit":20})
    en=next((x for x in current if x["attributes"].get("locale")=="en-US"),None)
    what=(
      "Please test the interactive tutorial and make moves on the highlighted "
      "board. Try Easy, Medium and Hard computer opponents, then local two-player "
      "mode. If possible, try an online Game Center match with a second tester, "
      "including turn handoffs and rematch. Check game replays, statistics and "
      "light/dark settings. The $1.99 Remove Ads purchase is a non-consumable; "
      "sandbox purchases should remove banner ads and Restore Purchases should "
      "restore access. Use TestFlight to report any confusing behavior or crashes."
    )
    if en:
        if en["attributes"].get("whatsNew")!=what:
            request("PATCH",f"/v1/betaBuildLocalizations/{en['id']}",{
                "data":{"type":"betaBuildLocalizations","id":en["id"],
                        "attributes":{"whatsNew":what}}
            })
            print("Updated English What to Test text")
        else:print("English What to Test already matches")
        locid=en["id"]
    else:
        created=request("POST","/v1/betaBuildLocalizations",{
           "data":{"type":"betaBuildLocalizations",
                   "attributes":{"locale":"en-US","whatsNew":what},
                   "relationships":{"build":ref("builds",bid)}}
        })["data"]
        locid=created["id"]
        print("Created English What to Test instructions")
    check=data(f"/v1/betaBuildLocalizations/{locid}")
    if check["attributes"].get("whatsNew")!=what:
        raise RuntimeError("Apple did not retain What to Test")
    print("Verified beta instructions on VALID build",BUILD)

    beta_apps=data(f"/v1/apps/{APP_ID}/betaAppLocalizations",{"limit":100})
    loc=next((x for x in beta_apps if x["attributes"].get("locale")=="en-US"),None)
    attrs={
       "description":"A twist on tic tac toe: nine connected boards, computer opponents, pass-and-play and Game Center turn-based matches.",
       "marketingUrl":"https://killjoy00.github.io/Ultttt/",
       "privacyPolicyUrl":"https://github.com/killjoy00/Ultttt/blob/main/PRIVACY.md"
    }
    if loc:
        changed={k:v for k,v in attrs.items() if loc["attributes"].get(k)!=v}
        if changed:
            request("PATCH",f"/v1/betaAppLocalizations/{loc['id']}",{
                "data":{"type":"betaAppLocalizations","id":loc["id"],"attributes":changed}
            })
            print("Updated English TestFlight app description and URLs")
    else:
        created=request("POST","/v1/betaAppLocalizations",{
            "data":{"type":"betaAppLocalizations",
                    "attributes":{"locale":"en-US",**attrs},
                    "relationships":{"app":ref("apps",APP_ID)}}
        })["data"]
        loc=created
        print("Created English TestFlight app description")
    final=data(f"/v1/betaAppLocalizations/{loc['id']}")
    if any(final["attributes"].get(k)!=v for k,v in attrs.items()):
        raise RuntimeError("TestFlight description/privacy URL readback mismatch")
    print("Verified TestFlight description, developer website and privacy URL")
    # A feedback email is not fabricated: only the account owner can authorize
    # a support inbox. The TestFlight app itself can collect beta feedback.

def review_note():
    items=data(f"/v1/apps/{APP_ID}/inAppPurchasesV2",{"limit":100})
    prod=next((x for x in items if x["attributes"].get("productId")==IAP),None)
    if not prod:
        raise RuntimeError("IAP not present")
    if prod["attributes"].get("inAppPurchaseType")!="NON_CONSUMABLE":
        raise RuntimeError("IAP type mismatch")
    note=("Remove Ads is a one-time non-consumable IAP ($1.99 in the US). "
          "Launch the game and tap the three-dot menu at the top right, then "
          "'Remove ads'. The sheet shows the StoreKit product price and a "
          "'Restore purchases' button. After a verified transaction, the "
          "bottom banner ad is no longer requested. No other game modes "
          "are locked behind this purchase. Test with an Apple sandbox account.")
    product_id=prod["id"]
    if prod["attributes"].get("reviewNote")!=note:
        request("PATCH",f"/v2/inAppPurchases/{product_id}",{
            "data":{"type":"inAppPurchases","id":product_id,
                    "attributes":{"reviewNote":note}}
        })
        print("Saved Apple Remove Ads review instructions")
    else:
        print("Remove Ads review instructions already match")
    final=data(f"/v2/inAppPurchases/{product_id}")
    if final["attributes"].get("reviewNote")!=note:
        raise RuntimeError("Apple review note verification failed")
    print("Verified Remove Ads review note; purchase remains draft, not submitted")

stage("English TestFlight beta metadata",testflight_metadata)
stage("Remove Ads review instructions",review_note)
if failures:
    raise SystemExit("Some Apple metadata remains outstanding: "+", ".join(failures))
print("\nDone: TestFlight metadata and Remove Ads review instructions.")
print("No App Review submission, external beta, or public release performed.")
