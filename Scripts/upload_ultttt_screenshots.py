#!/usr/bin/env python3
"""Upload real Ultimate Tic/Tac/Toe SwiftUI captures to the existing iOS App Store draft.

Safeguards:
- requires the exact Ultimate Tic/Tac/Toe bundle ID and an existing PREPARE_FOR_SUBMISSION version;
- modifies screenshots only for en-US APP_IPHONE_67;
- append-only and idempotent; never deletes or reorders another screenshot;
- no App Review, privacy, pricing, age-rating, app metadata or build changes.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.parse
import urllib.request
import urllib.error

import jwt
from PIL import Image

API="https://api.appstoreconnect.apple.com/v1"
EXPECTED_BUNDLE="com.killjoy00.ultttt"
DISPLAY_TYPE="APP_IPHONE_67"
WIDTH,HEIGHT=1290,2796
SELECTION=[
    ("board","01-board"),
    ("setup","02-setup"),
    ("tutorial","03-tutorial"),
    ("statistics","04-statistics"),
    ("settings","05-settings"),
]
request=json.loads(Path(".github/ultttt-screenshots-request.json").read_text())
assert request["repository"]=="killjoy00/Ultttt"
assert re.fullmatch(r"[a-f0-9]{40}",request["sha"])
assert request["bundleID"]==EXPECTED_BUNDLE
assert request["appName"]=="Ultimate Tic/Tac/Toe"
assert request["version"]=="1.0"
mode=request["mode"]
assert mode in ("inspect","apply")
raw=os.environ["ASC_KEY_P8"].replace("\r","").strip()
if "BEGIN PRIVATE KEY" not in raw:
    if "\\n" in raw:
        raw=raw.replace("\\n","\n")
    else:
        try:
            decoded=base64.b64decode(raw,validate=True).decode().strip()
            if "BEGIN PRIVATE KEY" in decoded:
                raw=decoded
        except (ValueError,UnicodeDecodeError):
            pass
if "BEGIN PRIVATE KEY" not in raw:
    body=re.sub(r"\s+","",raw)
    raw="-----BEGIN PRIVATE KEY-----\n"+"\n".join(body[i:i+64] for i in range(0,len(body),64))+"\n-----END PRIVATE KEY-----\n"
now=int(time.time())
token=jwt.encode({"iss":os.environ["ASC_ISSUER_ID"],"iat":now,"exp":now+1100,"aud":"appstoreconnect-v1"},raw,algorithm="ES256",headers={"kid":os.environ["ASC_KEY_ID"]})
HEADERS={"Authorization":"Bearer "+token,"Content-Type":"application/json","Accept":"application/json"}
def api(verb,path,data=None):
    url=API+"/"+path
    payload=json.dumps(data).encode() if data is not None else None
    req=urllib.request.Request(url,data=payload,headers=HEADERS,method=verb)
    try:
        with urllib.request.urlopen(req,timeout=60) as resp:
            payload=resp.read()
            return json.loads(payload) if payload else {}
    except urllib.error.HTTPError as exc:
        details=exc.read().decode("utf-8",errors="replace")
        raise RuntimeError("Apple API "+verb+" "+path.split("?")[0]+" HTTP "+str(exc.code)+": "+details[:600]) from None
def page(path):
    return api("GET",path).get("data",[])
def put_blob(url,method,headers,piece):
    # Apple upload URLs are temporary, unauthenticated and sensitive.
    req=urllib.request.Request(url,data=piece,headers=headers,method=method)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req,timeout=120) as resp:
                resp.read()
            return
        except (urllib.error.HTTPError,urllib.error.URLError) as exc:
            if attempt==2:
                raise RuntimeError("Apple screenshot binary upload failed after retries ("+str(getattr(exc,"code","network"))+")") from None
            time.sleep(3*(attempt+1))
def fields(item):
    return item.get("attributes",{})
def find_app():
    q=urllib.parse.urlencode({"filter[bundleId]":EXPECTED_BUNDLE,"limit":1})
    matches=page("apps?"+q)
    if len(matches)!=1:
        raise RuntimeError("Existing Ultimate Tic/Tac/Toe App Store Connect app record was not found; no uploads")
    if fields(matches[0]).get("name")!="Ultimate Tic/Tac/Toe":
        raise RuntimeError("Apple app name mismatch; refusing screenshot upload")
    return matches[0]
app=find_app()
appid=app["id"]
versions=page("apps/"+appid+"/appStoreVersions?limit=50")
editable=[v for v in versions if fields(v).get("platform")=="IOS" and fields(v).get("appStoreState")=="PREPARE_FOR_SUBMISSION"]
if not editable:
    raise RuntimeError("No editable iOS draft version. Do not create or submit a new version automatically.")
target=next((v for v in editable if fields(v).get("versionString")=="1.0"),None)
if target is None:
    raise RuntimeError("No editable Apple iOS 1.0 draft; refusing to alter another version")
locs=page("appStoreVersions/"+target["id"]+"/appStoreVersionLocalizations?limit=50")
locale=next((loc for loc in locs if fields(loc).get("locale")=="en-US"),None)
if not locale:
    raise RuntimeError("No existing en-US iOS draft listing localization; no screenshots uploaded.")
print("Verified Apple app",appid,"iOS",fields(target).get("versionString"),"state",fields(target).get("appStoreState"),"locale",locale["id"])
sets=page("appStoreVersionLocalizations/"+locale["id"]+"/appScreenshotSets?limit=50")
shotset=next((s for s in sets if fields(s).get("screenshotDisplayType")==DISPLAY_TYPE),None)
if shotset:
    print("Existing 6.9-inch iPhone screenshot set",shotset["id"])
else:
    print("No 6.9-inch screenshot set yet")
if mode=="inspect":
    for item in sets:
        sid=item["id"]
        screens=page("appScreenshotSets/"+sid+"/appScreenshots?limit=50")
        print("Existing screenshot set",fields(item).get("screenshotDisplayType"),"count",len(screens),"names",[fields(s).get("fileName") for s in screens])
    print("INSPECT ONLY; Apple image listing unchanged.")
    sys.exit(0)
if shotset is None:
    resp=api("POST","appScreenshotSets",{"data":{"type":"appScreenshotSets","attributes":{"screenshotDisplayType":DISPLAY_TYPE},"relationships":{"appStoreVersionLocalization":{"data":{"type":"appStoreVersionLocalizations","id":locale["id"]}}}}})
    shotset=resp["data"]
    print("Created new 6.9-inch screenshot set",shotset["id"])
setid=shotset["id"]
existing=page("appScreenshotSets/"+setid+"/appScreenshots?limit=50")
by_name={fields(s).get("fileName"):s for s in existing}
if len(existing)+sum(("ultttt-"+name+".png") not in by_name for _,name in SELECTION)>10:
    raise RuntimeError("Would exceed Apple's 10 screenshot limit; leaving existing screenshots unchanged")
source_dir=Path(os.environ["SCREENSHOT_SOURCE"])
out_dir=Path(os.environ.get("RUNNER_TEMP","/tmp"))/"ultttt-appstore"
out_dir.mkdir(parents=True,exist_ok=True)
pending=[]
hashes=set()
for input_name,slug in SELECTION:
    src=source_dir/(input_name+".png")
    if not src.is_file():
        raise RuntimeError("Missing captured Godot UI screenshot "+input_name)
    with Image.open(src) as im:
        if im.size!=(1290,2796):
            raise RuntimeError("Unexpected source screen dimensions "+str(im.size))
        image=im.convert("RGB")
    output=out_dir/("ultttt-"+slug+".png")
    image.save(output,format="PNG",optimize=True)
    digest=hashlib.sha256(output.read_bytes()).hexdigest()
    if digest in hashes:
        raise RuntimeError("Duplicate screenshot pixels detected: "+slug)
    hashes.add(digest)
    if output.stat().st_size<=25000:
        raise RuntimeError("Unusually small screenshot image "+slug)
    current=by_name.get(output.name)
    if current:
        state=fields(current).get("assetDeliveryState",{}).get("state")
        if state=="COMPLETE":
            print("Already complete:",output.name)
            continue
        raise RuntimeError("Screenshot name exists but isn't COMPLETE: "+output.name+" state="+str(state)+" (manual inspection before replacing)")
    pending.append(output)
    print("Ready:",output.name,WIDTH,"x",HEIGHT,output.stat().st_size,"bytes")
for output in pending:
    binary=output.read_bytes()
    request_payload={"data":{"type":"appScreenshots","attributes":{"fileName":output.name,"fileSize":len(binary)},"relationships":{"appScreenshotSet":{"data":{"type":"appScreenshotSets","id":setid}}}}}
    item=api("POST","appScreenshots",request_payload)["data"]
    item_id=item["id"]
    operations=fields(item).get("uploadOperations",[])
    if not operations:
        raise RuntimeError("Apple supplied no upload operations for "+output.name)
    for op in operations:
        length,offset=int(op["length"]),int(op["offset"])
        if offset<0 or length<1 or offset+length>len(binary):
            raise RuntimeError("Invalid Apple screenshot upload byte range")
        headers={h["name"]:h["value"] for h in op.get("requestHeaders",[])}
        put_blob(op["url"],op["method"],headers,binary[offset:offset+length])
    checksum=hashlib.md5(binary).hexdigest()
    api("PATCH","appScreenshots/"+item_id,{"data":{"type":"appScreenshots","id":item_id,"attributes":{"uploaded":True,"sourceFileChecksum":checksum}}})
    print("Uploaded and committed:",output.name,"Apple ID",item_id)
expected_names=set(by_name)|{p.name for p in pending}
for attempt in range(16):
    all_records=page("appScreenshotSets/"+setid+"/appScreenshots?limit=50")
    states={fields(x).get("fileName"):fields(x).get("assetDeliveryState",{}).get("state") for x in all_records}
    errors={fields(x).get("fileName"):fields(x).get("assetDeliveryState",{}).get("errors") for x in all_records if fields(x).get("assetDeliveryState",{}).get("state")=="FAILED"}
    if errors:
        raise RuntimeError("Apple screenshot processing failed "+repr(errors))
    missing=expected_names-set(states)
    pending_states={name:states.get(name,"MISSING") for name in expected_names if states.get(name)!="COMPLETE"}
    if not missing and not pending_states:
        print("SUCCESS: Apple confirms",len(expected_names),"COMPLETE screenshots on iOS 1.0",DISPLAY_TYPE)
        break
    if attempt==15:
        raise RuntimeError("Screenshot upload completed but Apple still processing: "+repr(pending_states))
    print("Waiting for Apple screenshot processing:",len(pending_states),"remaining")
    time.sleep(5)
print("No build, app submission, privacy, pricing or metadata changes.")
