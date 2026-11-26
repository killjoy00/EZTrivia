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

def invite_account_holder_if_available():
    # The Account Holder is the unique owner of this Apple developer account.
    # The connected API key may not have Users & Access scope; if so, leave
    # selection to the user in the App Store Connect user interface.
    groups=rows(f"/apps/{APP_ID}/betaGroups",{"limit":100}) or []
    selected=next((g for g in groups if g["attributes"].get("isInternalGroup") and
                   g["attributes"].get("name")=="Internal Testing"),None)
    if not selected:raise RuntimeError("Internal testing group is missing")
    gid=selected["id"]
    existing=rows(f"/betaGroups/{gid}/betaTesters",{"limit":100},optional=True)
    if existing:
        print("Internal beta group already has",len(existing),"tester(s); no new invitations")
        return
    holders=rows("/users",{"filter[roles]":"ACCOUNT_HOLDER","limit":10},optional=True)
    if holders is None:
        print("Apple API key cannot list account users; owner must select their tester from App Store Connect.")
        return
    if len(holders)!=1:
        print("Account Holder selection is ambiguous; manual tester selection required.")
        return
    person=holders[0]["attributes"]
    email=person.get("username","").strip()
    if "@" not in email:
        print("Apple Account Holder user has no email username in this API response; invite in App Store Connect.")
        return
    # Do not disclose or log the owner's Apple Account email.
    matches=rows("/betaTesters",{"filter[email]":email,"limit":10},optional=True)
    if matches is None:
        print("Could not check existing TestFlight testers; owner must join in App Store Connect.")
        return
    try:
        if matches:
            tester=matches[0]
            call("POST",f"/betaGroups/{gid}/relationships/betaTesters",{
                "data":[{"type":"betaTesters","id":tester["id"]}]
            })
            print("Added existing Account Holder beta tester to Internal Testing.")
        else:
            payload={"data":{"type":"betaTesters",
                "attributes":{"email":email,
                              "firstName":person.get("firstName") or "",
                              "lastName":person.get("lastName") or ""},
                "relationships":{
                    "betaGroups":{"data":[{"type":"betaGroups","id":gid}]},
                    "apps":{"data":[{"type":"apps","id":APP_ID}]}
                }
            }}
            call("POST","/betaTesters",payload)
            print("Invited the account's only Account Holder to Internal Testing.")
        verified=rows(f"/betaGroups/{gid}/betaTesters",{"limit":100}) or []
        print("Verified TestFlight tester count:",len(verified))
    except Exception as exc:
        # A tester invite can be prevented by this credential's role even when
        # game metadata and group creation are permitted. Preserve all other
        # successful configuration and leave a precise user-side step.
        print("Could not auto-invite Account Holder:",str(exc)[:250])
        print("Manual: TestFlight → Internal Testing → Testers (+) → select your Apple account.")

def verify_testflight_build():
    builds=rows("/builds",{"filter[app]":APP_ID,"sort":"-uploadedDate","limit":25},optional=True)
    if builds is None:
        print("Cannot query TestFlight builds with this API key; use the TestFlight page.")
        return
    wanted=next((x for x in builds if x["attributes"].get("version")=="100007"),None)
    if not wanted:
        print("Build 100007 has been uploaded but is not yet returned by Apple's processed build list.")
        return
    attrs=wanted["attributes"]
    print("Apple registered TestFlight build 100007, state:",attrs.get("processingState"))
    if attrs.get("processingState")=="VALID":
        print("Build 100007 is processed and valid for TestFlight.")
        group=next((g for g in rows(f"/apps/{APP_ID}/betaGroups",{"limit":100}) or []
                    if g["attributes"].get("name")=="Internal Testing" and
                    g["attributes"].get("isInternalGroup")),None)
        if group:
            eligible=rows(f"/betaGroups/{group['id']}/builds",{"limit":50},optional=True) or []
            linked=any(x["id"]==wanted["id"] for x in eligible)
            if not linked and not group["attributes"].get("hasAccessToAllBuilds"):
                call("POST",f"/builds/{wanted['id']}/relationships/betaGroups",{
                    "data":[{"type":"betaGroups","id":group["id"]}]
                })
                print("Associated processed TestFlight build with internal group.")
            else:
                print("Internal group auto-access:",group["attributes"].get("hasAccessToAllBuilds"),
                      "build listed in group:",linked)
    else:
        print("Apple processing continues; the internal group will get the build when ready.")

def set_developer_website():
    # Existing GitHub Pages user site already publishes the correct
    # AdMob seller line at its ROOT (/app-ads.txt). The app landing page is
    # under /Ultttt/, so Google can resolve the publisher from the root.
    url="https://killjoy00.github.io/Ultttt/"
    versions=rows(f"/apps/{APP_ID}/appStoreVersions",{"limit":30}) or []
    target=next((v for v in versions if v["attributes"].get("platform")=="IOS" and
                 v["attributes"].get("versionString")=="1.0" and
                 v["attributes"].get("appStoreState")=="PREPARE_FOR_SUBMISSION"),None)
    if not target:
        raise RuntimeError("No editable Apple iOS 1.0 version")
    locs=rows(f"/appStoreVersions/{target['id']}/appStoreVersionLocalizations",
              {"limit":50})
    loc=next((x for x in locs if x["attributes"].get("locale")=="en-US"),None)
    if not loc:raise RuntimeError("No English store localization")
    if loc["attributes"].get("marketingUrl")!=url:
        call("PATCH",f"/appStoreVersionLocalizations/{loc['id']}",{
            "data":{"type":"appStoreVersionLocalizations","id":loc["id"],
                    "attributes":{"marketingUrl":url}}
        })
        print("Saved Apple Marketing URL for existing developer website.")
    final=data(f"/appStoreVersionLocalizations/{loc['id']}")
    if final["attributes"].get("marketingUrl")!=url:
        raise RuntimeError("Apple Marketing URL readback mismatch")
    print("Verified Marketing URL:",url)
    print("Google app-ads.txt seller entry already in the developer site's root repo.")
    print("Google AdMob still needs the app and live banner unit IDs before public release.")

stage("Age rating",apply_age_rating)
stage("Category",apply_categories)
stage("Developer website for AdMob",set_developer_website)
stage("Internal TestFlight group",ensure_internal_group)
stage("App price audit",inspect_app_price)
stage("Account Holder internal tester invitation",invite_account_holder_if_available)
stage("TestFlight build processing verification",verify_testflight_build)
if errors:raise SystemExit("Incomplete Apple setup (safe to rerun): "+", ".join(errors))
print("\nFinished age rating, Games/Board category and internal beta setup.")
print("No public App Review or TestFlight external beta submission.")
