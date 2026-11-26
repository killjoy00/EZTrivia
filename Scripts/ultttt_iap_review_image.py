#!/usr/bin/env python3
"""Upload one actual SwiftUI Remove Ads sheet screenshot for Apple IAP review.

Never overwrite an existing Apple image, price, app, or purchase metadata.
No public App Review submission.
"""
from __future__ import annotations
import os,base64,re,time,hashlib,json
from pathlib import Path
from urllib.parse import urlparse
import requests,jwt
from PIL import Image,ImageOps,ImageStat

APPID="6835609349"
BUNDLE="com.killjoy00.ultttt"
NAME="Ultimate Tic/Tac/Toe"
SKU="com.killjoy00.ultttt.remove_ads"
image_path=Path(os.environ["RUNNER_TEMP"])/"iap-review"/"ultttt-remove-ads-review.png"
if not image_path.is_file():raise SystemExit("Missing real Remove Ads screenshot")
with Image.open(image_path) as check:
    if check.size!=(1290,2796) or check.mode!="RGB":
        raise SystemExit(f"Unexpected IAP screenshot size and color: {check.size}, {check.mode}")
    if ImageStat.Stat(check.resize((50,100))).stddev[0]<10:
        raise SystemExit("IAP screen appears blank")
binary=image_path.read_bytes()
if len(binary)<20_000:raise SystemExit("Review screenshot unexpectedly small")

raw=os.environ["ASC_KEY_P8"].replace("\r","").strip()
if "BEGIN PRIVATE KEY" not in raw:
    if "\\n" in raw:raw=raw.replace("\\n","\n")
    else:
        try:
            decoded=base64.b64decode(raw).decode()
            if "BEGIN PRIVATE KEY" in decoded:raw=decoded
        except (ValueError,UnicodeDecodeError):pass
if "BEGIN PRIVATE KEY" not in raw:
    compact="".join(raw.split())
    raw=("-----BEGIN PRIVATE KEY-----\n"
         +"\n".join(compact[n:n+64] for n in range(0,len(compact),64))
         +"\n-----END PRIVATE KEY-----\n")
now=int(time.time())
token=jwt.encode({"iss":os.environ["ASC_ISSUER_ID"],"iat":now,"exp":now+1100,"aud":"appstoreconnect-v1"},
                 raw,algorithm="ES256",headers={"kid":os.environ["ASC_KEY_ID"]})
s=requests.Session()
s.headers.update({"Authorization":"Bearer "+token,"Content-Type":"application/json","Accept":"application/json"})
base="https://api.appstoreconnect.apple.com"

def api(method,path,body=None,params=None,optional=False):
    try:r=s.request(method,base+path,json=body,params=params,timeout=60)
    except requests.RequestException as exc:raise RuntimeError(f"Apple {method} {path} network failure") from exc
    if r.status_code>=300:
        if optional and r.status_code==404:return None
        try:details="; ".join(str(x.get("code"))+": "+str(x.get("detail","")) for x in r.json().get("errors",[])[:2])
        except (ValueError,TypeError):details=""
        raise RuntimeError(f"Apple {method} {path} HTTP {r.status_code}: {details[:250]}")
    return r.json() if r.content else {}

apps=api("GET","/v1/apps",params={"filter[bundleId]":BUNDLE,"limit":5})["data"]
if len(apps)!=1 or apps[0]["id"]!=APPID or apps[0]["attributes"].get("name")!=NAME:
    raise SystemExit("Wrong Apple app record; no screenshot uploaded")
items=api("GET",f"/v1/apps/{APPID}/inAppPurchasesV2",params={"limit":100})["data"]
product=next((x for x in items if x["attributes"].get("productId")==SKU),None)
if not product or product["attributes"].get("inAppPurchaseType")!="NON_CONSUMABLE":
    raise SystemExit("Wrong Remove Ads product; no screenshot uploaded")
pid=product["id"]
print("Verified Apple non-consumable",SKU,"ID",pid)

existing=api("GET",f"/v2/inAppPurchases/{pid}/appStoreReviewScreenshot",optional=True)
old=existing.get("data") if existing else None
if old:
    state=old["attributes"].get("assetDeliveryState",{}).get("state")
    if state=="COMPLETE":
        print("A review screenshot already exists and is COMPLETE; keeping it unchanged.")
        raise SystemExit(0)
    raise RuntimeError("An unfinished existing IAP review image exists; refusing to replace it automatically")

print("Uploading actual SwiftUI Remove Ads IAP sheet, 1290×2796.")
item=api("POST","/v1/inAppPurchaseAppStoreReviewScreenshots",{
    "data":{"type":"inAppPurchaseAppStoreReviewScreenshots",
            "attributes":{"fileName":image_path.name,"fileSize":len(binary)},
            "relationships":{"inAppPurchaseV2":{
                "data":{"type":"inAppPurchases","id":pid}
            }}}
})["data"]
iid=item["id"]
ops=item["attributes"].get("uploadOperations",[])
if not ops:raise RuntimeError("No Apple IAP upload operations returned")
for op in ops:
    uri=urlparse(op.get("url",""))
    if uri.scheme!="https" or not uri.hostname or uri.username or uri.password:
        raise RuntimeError("Unsafe Apple upload location")
    offset,length=int(op["offset"]),int(op["length"])
    if offset<0 or length<1 or offset+length>len(binary):raise RuntimeError("Invalid byte range")
    headers={x["name"]:x["value"] for x in op.get("requestHeaders",[])}
    resp=requests.request(op["method"],op["url"],data=binary[offset:offset+length],
                          headers=headers,timeout=140)
    if resp.status_code>=300:raise RuntimeError(f"Apple IAP screenshot byte upload HTTP {resp.status_code}")
api("PATCH",f"/v1/inAppPurchaseAppStoreReviewScreenshots/{iid}",{
    "data":{"type":"inAppPurchaseAppStoreReviewScreenshots","id":iid,
            "attributes":{"uploaded":True,
                          "sourceFileChecksum":hashlib.md5(binary).hexdigest()}}
})
print("Committed Apple IAP review screenshot",iid)
for attempt in range(24):
    found=api("GET",f"/v1/inAppPurchaseAppStoreReviewScreenshots/{iid}")["data"]
    state=found["attributes"].get("assetDeliveryState",{}).get("state")
    if state=="COMPLETE":
        final=api("GET",f"/v2/inAppPurchases/{pid}/appStoreReviewScreenshot")["data"]
        if final["id"]!=iid:raise RuntimeError("Apple product does not point to expected review screenshot")
        print("SUCCESS: Apple confirmed Remove Ads review screenshot COMPLETE, image",iid)
        print("This is private App Review information; no public release or submission.")
        break
    if state=="FAILED":raise RuntimeError("Apple IAP review screenshot rejected: "+str(found["attributes"].get("assetDeliveryState")))
    if attempt==23:raise RuntimeError("Apple screenshot is still processing; inspect draft later")
    time.sleep(5)
