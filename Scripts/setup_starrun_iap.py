#!/usr/bin/env python3
"""Idempotent creation of a non-consumable draft in the existing Star/Run app."""
import base64, json, os, time, urllib.request, urllib.error, urllib.parse
from pathlib import Path
import jwt
SKU="com.rsm.starrun.remove_ads"
BUNDLE="com.rsm.starrun"
def key():
    raw=os.environ["ASC_KEY_P8"].strip()
    if "\\n" in raw: raw=raw.replace("\\n","\n")
    if "BEGIN" not in raw:
        try:
            decoded=base64.b64decode(raw).decode()
            if "BEGIN" in decoded: raw=decoded
        except (ValueError, UnicodeDecodeError): pass
    if "BEGIN" not in raw:
        raw="-----BEGIN PRIVATE KEY-----\n"+"\n".join(raw[i:i+64] for i in range(0,len(raw),64))+"\n-----END PRIVATE KEY-----\n"
    return raw
def apple(method,path,body=None,params=None):
    now=int(time.time())
    token=jwt.encode({"iss":os.environ["ASC_ISSUER_ID"],"iat":now,"exp":now+420,"aud":"appstoreconnect-v1"},key(),algorithm="ES256",headers={"kid":os.environ["ASC_KEY_ID"]})
    url="https://api.appstoreconnect.apple.com"+path
    if params: url+="?"+urllib.parse.urlencode(params)
    data=json.dumps(body).encode() if body else None
    req=urllib.request.Request(url,method=method,data=data,headers={"Authorization":"Bearer "+token,"Accept":"application/json","Content-Type":"application/json"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req,timeout=40) as res: return json.load(res)
        except urllib.error.HTTPError as e:
            try:
                details="; ".join(x.get("code","")+": "+x.get("title","")+" - "+x.get("detail","") for x in json.load(e).get("errors",[])[:3])
            except (ValueError,UnicodeDecodeError): details="Check Apple account permissions and paid agreement"
            if e.code in (429,500,502,503,504) and attempt<2:
                time.sleep(5*(attempt+1));continue
            raise SystemExit("Apple "+method+" "+path+" HTTP "+str(e.code)+" "+details) from None
    raise SystemExit("Apple API retry limit")
def run():
    r=json.loads(Path(".github/starrun-iap-request.json").read_text())
    expected={"repository":"killjoy00/StarRun","bundleID":BUNDLE,"productID":SKU,"type":"NON_CONSUMABLE","mode":"create_draft"}
    if any(r.get(k)!=v for k,v in expected.items()): raise SystemExit("Unexpected request: refusing Apple mutation")
    apps=apple("GET","/v1/apps",params={"filter[bundleId]":BUNDLE,"limit":1})["data"]
    if len(apps)!=1 or apps[0]["attributes"]["bundleId"]!=BUNDLE: raise SystemExit("Existing Star/Run app not found")
    aid=apps[0]["id"]
    products=apple("GET",f"/v1/apps/{aid}/inAppPurchasesV2",params={"filter[productId]":SKU,"limit":20})["data"]
    if products:
        p=products[0]
        if p["attributes"].get("productId")!=SKU or p["attributes"].get("inAppPurchaseType")!="NON_CONSUMABLE":raise SystemExit("Unexpected IAP type")
        print("Existing product found; no duplicate created.")
    else:
        p=apple("POST","/v2/inAppPurchases",{"data":{"type":"inAppPurchases","attributes":{"name":"Remove Ads","productId":SKU,"inAppPurchaseType":"NON_CONSUMABLE"},"relationships":{"app":{"data":{"type":"apps","id":aid}}}}})["data"]
        print("Created Apple non-consumable.")
    pid=p["id"]
    versions=apple("GET",f"/v2/inAppPurchases/{pid}/versions",params={"limit":20})["data"]
    drafts=[v for v in versions if v["attributes"].get("state")=="PREPARE_FOR_SUBMISSION"]
    if drafts: version=drafts[0]
    elif versions: raise SystemExit("Existing IAP has no editable draft; leaving reviewed metadata alone")
    else:
        version=apple("POST","/v1/inAppPurchaseVersions",{"data":{"type":"inAppPurchaseVersions","relationships":{"inAppPurchase":{"data":{"type":"inAppPurchases","id":pid}}}}})["data"]
    vid=version["id"]
    locs=apple("GET",f"/v1/inAppPurchaseVersions/{vid}/localizations",params={"limit":50})["data"]
    if not any(l.get("attributes",{}).get("locale")=="en-US" for l in locs):
        apple("POST","/v2/inAppPurchaseLocalizations",{"data":{"type":"inAppPurchaseLocalizations","attributes":{"locale":"en-US","name":"Remove Ads","description":"Permanently removes banner ads from Star/Run."},"relationships":{"version":{"data":{"type":"inAppPurchaseVersions","id":vid}}}}})
        print("Created en-US localization.")
    else: print("Existing en-US localization preserved.")
    check=apple("GET",f"/v1/apps/{aid}/inAppPurchasesV2",params={"filter[productId]":SKU,"limit":20})["data"]
    if not check or check[0]["attributes"].get("productId")!=SKU:raise SystemExit("Failed Apple SKU readback")
    print("APPLE SKU VERIFIED:",SKU,"IAP ID",pid,"DRAFT VERSION",vid)
    print("NO PRICE SET; NO APP REVIEW SUBMISSION.")
    with Path(os.environ.get("GITHUB_STEP_SUMMARY","/dev/null")).open("a") as f:
        f.write("Star/Run Remove Ads product created/verified. SKU: "+SKU+"\nApple IAP ID: "+pid+"\nDraft version: "+vid+"\nNo price or App Review submission was made.\n")
if __name__=="__main__":run()
