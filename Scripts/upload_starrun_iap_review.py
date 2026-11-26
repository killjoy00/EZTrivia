#!/usr/bin/env python3
"""Upload the actual Godot Remove Ads Settings UI to App Store Connect review."""
import os,json,time,hashlib,urllib.request,urllib.error
from pathlib import Path
from PIL import Image,ImageStat
from setup_starrun_iap import apple,SKU
ID="6835610167"
RESOURCE="inAppPurchaseAppStoreReviewScreenshots"

def optional(path):
    try:return apple("GET",path)
    except SystemExit as e:
        if "HTTP 404" in str(e):return None
        raise

def run():
    item=apple("GET",f"/v2/inAppPurchases/{ID}")["data"]
    if item["attributes"].get("productId")!=SKU or item["attributes"].get("inAppPurchaseType")!="NON_CONSUMABLE":
        raise SystemExit("Apple product mismatch. Refusing screenshot modification")
    source=Path(os.environ["SCREENSHOT_SOURCE"])/"390x844"/"settings.png"
    if not source.is_file():raise SystemExit("Godot iPhone Settings screenshot missing")
    final=Path(os.environ["RUNNER_TEMP"])/"StarRun-Remove-Ads-Review.png"
    with Image.open(source) as img:
        if img.size!=(390,844):raise SystemExit("Unexpected Godot screenshot dimensions")
        if sum(ImageStat.Stat(img.convert("RGB")).stddev)<22:raise SystemExit("Blank screenshot rejected")
        img.convert("RGB").resize((1290,2796),Image.Resampling.LANCZOS).save(final,optimize=True)
    contents=final.read_bytes()
    print("Authentic Godot UI review screenshot",final.name, "1290x2796",len(contents),"bytes")
    existing=optional(f"/v2/inAppPurchases/{ID}/appStoreReviewScreenshot")
    if existing and existing.get("data"):
        old=existing["data"];state=old.get("attributes",{}).get("assetDeliveryState",{}).get("state","")
        if state in ("COMPLETE","UPLOAD_COMPLETE","PROCESSING"):
            print("Existing Apple review screenshot retained",old["id"],state)
            return
        raise SystemExit("Screenshot reservation exists in state "+state+"; retry manually after examining it")
    shot=apple("POST",f"/v1/{RESOURCE}",{"data":{
      "type":RESOURCE,"attributes":{"fileName":final.name,"fileSize":len(contents)},
      "relationships":{"inAppPurchaseV2":{"data":{"type":"inAppPurchases","id":ID}}}
    }})["data"]
    sid=shot["id"]
    ops=shot.get("attributes",{}).get("uploadOperations",[])
    if not ops:raise SystemExit("Apple returned no upload operations")
    for op in ops:
        start=int(op["offset"]);size=int(op["length"])
        piece=contents[start:start+size]
        if len(piece)!=size:raise SystemExit("Bad Apple upload offset")
        headers={h["name"]:h["value"] for h in op.get("requestHeaders",[])}
        for retry in range(3):
            try:
                req=urllib.request.Request(op["url"],data=piece,headers=headers,method=op.get("method","PUT"))
                with urllib.request.urlopen(req,timeout=90) as response:response.read()
                break
            except (urllib.error.HTTPError,urllib.error.URLError):
                if retry==2:raise SystemExit("Apple screenshot binary upload failed")
                time.sleep((retry+1)*3)
    apple("PATCH",f"/v1/{RESOURCE}/{sid}",{"data":{"type":RESOURCE,"id":sid,
        "attributes":{"uploaded":True,"sourceFileChecksum":hashlib.md5(contents).hexdigest()}}})
    for i in range(10):
        detail=apple("GET",f"/v1/{RESOURCE}/{sid}")["data"]["attributes"]
        state=detail.get("assetDeliveryState",{}).get("state","UNKNOWN")
        if state in ("COMPLETE","UPLOAD_COMPLETE"):
            print("APPLE REVIEW SCREENSHOT VERIFIED:",sid,state)
            break
        if state in ("FAILED","ERROR","UPLOAD_FAILED"):raise SystemExit("Apple could not process screenshot")
        print("Screenshot Apple processing:",state)
        time.sleep(6)
    else:print("Screenshot uploaded, processing pending")
    with Path(os.environ.get("GITHUB_STEP_SUMMARY","/dev/null")).open("a") as f:
        f.write("Remove Ads screenshot uploaded to existing Star/Run Apple IAP "+ID+", state "+state+". No release or submission.\n")
if __name__=="__main__":run()
