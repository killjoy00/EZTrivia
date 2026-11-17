#!/usr/bin/env python3
"""Configure only verified Ultttt age, game category, internal beta group.

The user's approved game is a nonviolent, ad-supported board game with
Game Center turn-based matches but no text/voice chat, public user content,
gambling, contests/prizes, or unrestricted browsing.

No public App Review submission, app pricing change, or advertiser setting.
"""
from __future__ import annotations
import base64
import json
import os
import re
import time
from urllib.parse import urlencode
import jwt
import requests

BUNDLE="com.killjoy00.ultttt"
NAME="Ultimate Tic/Tac/Toe"
APP_ID="6835609349"
ROOT="https://api.appstoreconnect.apple.com/v1"
raw=os.environ["ASC_KEY_P8"].replace("\r","").strip()
if "BEGIN PRIVATE KEY" not in raw:
    if "\\n" in raw:
        raw=raw.replace("\\n","\n")
    else:
        try:
            decoded=base64.b64decode(raw).decode()
            if "BEGIN PRIVATE KEY" in decoded:raw=decoded
        except (UnicodeDecodeError, ValueError):
            pass
if "BEGIN PRIVATE KEY" not in raw:
    compact="".join(raw.split())
    raw=("-----BEGIN PRIVATE KEY-----\n"
         +"\n".join(compact[n:n+64] for n in range(0,len(compact),64))
         +"\n-----END PRIVATE KEY-----\n")
now=int(time.time())
token=jwt.encode({"iss":os.environ["ASC_ISSUER_ID"],"iat":now,"exp":now+1200,
                  "aud":"appstoreconnect-v1"},raw,algorithm="ES256",
                 headers={"kid":os.environ["ASC_KEY_ID"]})
session=requests.Session()
session.headers.update({"Authorization":"Bearer "+token,"Accept":"application/json",
                        "Content-Type":"application/json"})

def call(method,path,body=None,params=None,optional=False):
    url=path if path.startswith("https://") else ROOT+path
    try:
        res=session.request(method,url,json=body,params=params,timeout=40)
    except requests.RequestException as exc:
        raise RuntimeError(f"Apple {method} {path}: {type(exc).__name__}") from exc
    if not 200<=res.status_code<300:
        if optional and res.status_code in (403,404):
            print(f"Optional Apple endpoint {path} HTTP {res.status_code}")
            return None
        detail="; ".join(e.get("code","")+": "+e.get("title","")+": "+e.get("detail","")
                         for e in res.json().get("errors",[])[:2])
        raise RuntimeError(f"Apple {method} {path}: HTTP {res.status_code}, {detail[:340]}")
    return res.json() if res.content else {}

def data(path,params=None,optional=False):
    r=call("GET",path,params=params,optional=optional)
    return None if r is None else r.get("data")

def rows(path,params=None,optional=False):
    results=[]
    while path:
        r=call("GET",path,params=params,optional=optional)
        if r is None:return None
        results+=r.get("data",[])
        path=r.get("links",{}).get("next")
        params=None
    return results

def relate(kind,id):
    return {"data":{"type":kind,"id":id}}

apps=rows("/apps",{"filter[bundleId]":BUNDLE,"limit":5})
if len(apps)!=1 or apps[0]["id"]!=APP_ID or apps[0]["attributes"].get("name")!=NAME:
    raise SystemExit("App identity mismatch: refusing to edit a different app")
app=apps[0]
print("Verified App Store Connect",NAME,BUNDLE,"ID",APP_ID)
infos=rows(f"/apps/{APP_ID}/appInfos",{"limit":30}) or []
if not infos:raise SystemExit("No Apple appInfo record")
info=infos[0]
info_id=info["id"]
print("Using app info",info_id)

errors=[]

def stage(name,handler):
    print("\n== "+name+" ==")
    try:handler()
    except Exception as exc:
        print("::error::"+name+" failed: "+str(exc))
        errors.append(name)

def apply_age_rating():
    result=data(f"/appInfos/{info_id}/ageRatingDeclaration")
    if not result:
        raise RuntimeError("Apple did not return an age rating declaration")
    age_id=result["id"]
    existing=result.get("attributes",{})
    # A board-game rules review confirmed no chat or broad distribution of
    # user-created media; Game Center turn exchange is not social posting.
    declared={
        "advertising":True,
        "parentalControls":False,
        "ageAssurance":False,
        "unrestrictedWebAccess":False,
        "userGeneratedContent":False,
        "socialMedia":False,
        "messagingAndChat":False,
        "healthOrWellnessTopics":False,
        "gambling":False,
        "lootBox":False,
        "alcoholTobaccoOrDrugUseOrReferences":"NONE",
        "contests":"NONE",
        "gamblingSimulated":"NONE",
        "gunsOrOtherWeapons":"NONE",
        "medicalOrTreatmentInformation":"NONE",
        "profanityOrCrudeHumor":"NONE",
        "sexualContentGraphicAndNudity":"NONE",
        "sexualContentOrNudity":"NONE",
        "horrorOrFearThemes":"NONE",
        "matureOrSuggestiveThemes":"NONE",
        "violenceCartoonOrFantasy":"NONE",
        "violenceRealistic":"NONE",
        "violenceRealisticProlongedGraphicOrSadistic":"NONE"
    }
    changed={k:v for k,v in declared.items() if existing.get(k)!=v}
    if changed:
        call("PATCH",f"/ageRatingDeclarations/{age_id}",{
            "data":{"type":"ageRatingDeclarations","id":age_id,
                    "attributes":changed}
        })
        print("Updated",len(changed),"age questionnaire fields (includes advertising=true)")
    else:
        print("Age questionnaire already matches")
    final=data(f"/appInfos/{info_id}/ageRatingDeclaration")["attributes"]
    mismatch={k:(v,final.get(k)) for k,v in declared.items() if final.get(k)!=v}
    if mismatch:raise RuntimeError(f"Age declaration verification mismatches: {mismatch}")
    print("Verified age rating declarations; no Made for Kids setting or manual override.")
    info_updated=data(f"/appInfos/{info_id}")
    print("Calculated US App Store age rating:",info_updated.get("attributes",{}).get("appStoreAgeRating"))

def apply_categories():
    parent=data("/appCategories/GAMES",optional=True)
    if not parent:
        raise RuntimeError("Apple Games category is unavailable")
    children=rows("/appCategories/GAMES/subcategories",{"limit":200})
    board=next((x for x in children if x["id"]=="GAMES_BOARD"),None)
    if not board:
        board=next((x for x in children if x["id"].startswith("GAMES_") and
                    x["id"].endswith("_BOARD")),None)
    if not board:
        raise RuntimeError("Could not find Apple's Games/Board subcategory, no category changed")
    links={
      "primaryCategory":relate("appCategories","GAMES"),
      "primarySubcategoryOne":relate("appCategories",board["id"])
    }
    cur={}
    for key in links:
        d=data(f"/appInfos/{info_id}/relationships/{key}",optional=True)
        cur[key]=d.get("id") if d else None
    if all(cur[k]==v["data"]["id"] for k,v in links.items()):
        print("Already in category Games → Board.")
    else:
        call("PATCH",f"/appInfos/{info_id}",{
            "data":{"type":"appInfos","id":info_id,"relationships":links}
        })
        print("Saved Games → Board in App Store Connect")
    for key,expect in links.items():
        found=data(f"/appInfos/{info_id}/relationships/{key}",optional=True)
        foundid=found.get("id") if found else None
        if foundid!=expect["data"]["id"]:
            raise RuntimeError(f"Category {key} not confirmed: {foundid}")
    print("Verified category Games /",board["id"])

def ensure_internal_group():
    groups=rows(f"/apps/{APP_ID}/betaGroups",{"limit":100})
    group=next((x for x in groups if x["attributes"].get("isInternalGroup") is True and
               x["attributes"].get("name")=="Internal Testing"),None)
    if not group:
        group=call("POST","/betaGroups",{"data":{
          "type":"betaGroups",
          "attributes":{"name":"Internal Testing","isInternalGroup":True,
                        "hasAccessToAllBuilds":True},
          "relationships":{"app":relate("apps",APP_ID)}
        }})["data"]
        print("Created internal TestFlight group 'Internal Testing'")
    else:
        print("Internal TestFlight group exists")
        if not group["attributes"].get("hasAccessToAllBuilds"):
            call("PATCH",f"/betaGroups/{group['id']}",{
                "data":{"type":"betaGroups","id":group["id"],
                        "attributes":{"hasAccessToAllBuilds":True}}
            })
            print("Enabled automatic access to future builds")
    final=data(f"/betaGroups/{group['id']}")
    if final["attributes"].get("isInternalGroup") is not True:
        raise RuntimeError("Apple group is not internal")
    print("Verified internal group",group["id"],"auto-access:",
          final["attributes"].get("hasAccessToAllBuilds"))
    builds=rows(f"/apps/{APP_ID}/builds",{"limit":20},optional=True) or []
    print("Apple builds on app:",[(x["attributes"].get("version"),
                                 x["attributes"].get("processingState")) for x in builds])
    testers=rows(f"/betaGroups/{group['id']}/betaTesters",{"limit":100},optional=True)
    print("Internal tester accounts in group:",len(testers or []))
    if not testers:
        print("An eligible App Store Connect account must be selected for an internal tester invite.")

def inspect_app_price():
    schedule=data(f"/apps/{APP_ID}/appPriceSchedule",optional=True)
    if not schedule:
        print("App sale price has no current schedule. Free pricing may require confirmation before public release.")
        return
    schedule_id=schedule["id"]
    prices=rows(f"/appPriceSchedules/{schedule_id}/manualPrices",{"limit":50},optional=True)
    print("App price schedule exists",schedule_id,"manual entries:",len(prices or []))
    if prices:
        for item in prices[:5]:
            p=item.get("relationships",{}).get("appPricePoint",{}).get("data")
            if p and p.get("id"):
                detail=data(f"/appPricePoints/{p['id']}",optional=True)
                print("Price point",p["id"],detail.get("attributes",{}).get("customerPrice") if detail else "unavailable")
    print("No change made to the app's sale price.")

stage("Age rating",apply_age_rating)
stage("Category",apply_categories)
stage("Internal TestFlight group",ensure_internal_group)
stage("App price audit",inspect_app_price)
if errors:raise SystemExit("Incomplete Apple setup (safe to rerun): "+", ".join(errors))
print("\nFinished age rating, Games/Board category and internal beta setup.")
print("No public App Review or TestFlight external beta submission.")
