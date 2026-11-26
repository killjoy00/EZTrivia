#!/usr/bin/env python3
"""Idempotently prepare Ultttt metadata, Game Center and a draft IAP.

Runs only in killjoy00/EZTrivia GitHub Actions with its existing Apple secrets.
Does NOT set an IAP price, submit for review, create a public release, or
upload a binary. The source and listing are SHA-pinned by the workflow.
"""
from __future__ import annotations
import base64
import io
import json
import os
import pathlib
import time
from urllib.parse import urlparse

import jwt
import requests
from PIL import Image, ImageDraw, ImageFont

BASE = "https://api.appstoreconnect.apple.com"
APP_BUNDLE = "com.killjoy00.ultttt"
SOURCE = pathlib.Path("ultttt")
LISTING = json.loads((SOURCE / "docs/app-store/en-US.json").read_text())
assert LISTING["bundleID"] == APP_BUNDLE
assert LISTING["appName"] == "Ultimate Tic/Tac/Toe"
ACHIEVEMENTS = [
    ("first_win", "First Win", 10, "Win your first game against the computer.", "Your first solo victory.", "1"),
    ("five_wins", "Five Wins", 20, "Win five games against the computer.", "Five solo wins.", "5"),
    ("ten_wins", "Ten Wins", 30, "Win ten games against the computer.", "Ten solo wins.", "10"),
    ("first_online_win", "First Online Win", 20, "Win a Game Center match.", "Your first online victory.", "1"),
    ("twenty_five_online_wins", "25 Online Wins", 40, "Win 25 Game Center matches.", "Twenty-five online victories.", "25"),
]

def apple_key():
    raw = os.environ["ASC_KEY_P8"].replace("\r", "").strip()
    if "BEGIN PRIVATE KEY" not in raw:
        if "\\n" in raw:
            raw = raw.replace("\\n", "\n")
        else:
            try:
                decoded = base64.b64decode(raw).decode()
                if "BEGIN PRIVATE KEY" in decoded:
                    raw = decoded
            except (ValueError, UnicodeDecodeError):
                pass
    if "BEGIN PRIVATE KEY" not in raw:
        compact = "".join(raw.split())
        raw = "-----BEGIN PRIVATE KEY-----\n" + "\n".join(compact[i:i+64] for i in range(0, len(compact), 64)) + "\n-----END PRIVATE KEY-----\n"
    return raw

def auth():
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"},
        apple_key(), algorithm="ES256", headers={"kid": os.environ["ASC_KEY_ID"]}
    )

session = requests.Session()
session.headers.update({"Authorization": "Bearer " + auth(),
                        "Accept": "application/json", "Content-Type": "application/json"})

def query(method, path, payload=None, params=None):
    url = path if path.startswith("https://") else BASE + path
    response = session.request(method, url, json=payload, params=params, timeout=50)
    if response.status_code >= 300:
        detail = ""
        try:
            detail = "; ".join((e.get("code", "") + " " + e.get("title", "") + ": " + e.get("detail", ""))[:240]
                                for e in response.json().get("errors", [])[:2])
        except (ValueError, TypeError):
            detail = ""
        raise RuntimeError(f"Apple {method} {path.split('?')[0]} HTTP {response.status_code}: {detail}")
    return response.json() if response.content else {}

def get(path, params=None):
    return query("GET", path, params=params).get("data")

def all_pages(path, params=None):
    items = []
    url = path
    while url:
        data = query("GET", url, params=params)
        items.extend(data.get("data", []))
        url = data.get("links", {}).get("next")
        params = None
    return items

def ref(kind, value):
    return {"data": {"type": kind, "id": value}}

apps = all_pages("/v1/apps", {"filter[bundleId]": APP_BUNDLE, "limit": 10})
if len(apps) != 1:
    raise SystemExit(f"Expected one app with {APP_BUNDLE}, found {len(apps)}")
APP = apps[0]["id"]
print("Preparing draft metadata for", apps[0]["attributes"].get("name"), "app", APP)

def configure_listing():
    infos = all_pages(f"/v1/apps/{APP}/appInfos")
    if not infos:
        raise RuntimeError("No editable App Information record")
    infos = infos[:1]
    locs = all_pages(f"/v1/appInfos/{infos[0]['id']}/appInfoLocalizations")
    info = next((x for x in locs if x["attributes"].get("locale") == "en-US"), None)
    if info is None:
        raise RuntimeError("No en-US App Information localization")
    attrs = {"subtitle": LISTING["subtitle"], "privacyPolicyUrl": LISTING["privacyPolicyUrl"]}
    changed = {k: v for k, v in attrs.items() if info["attributes"].get(k) != v}
    if changed:
        query("PATCH", f"/v1/appInfoLocalizations/{info['id']}", {
            "data": {"type": "appInfoLocalizations", "id": info["id"], "attributes": changed}
        })
        print("Saved App Information:", ", ".join(changed))
    else:
        print("App Information already matches")
    if info["attributes"].get("name") != LISTING["appName"]:
        raise RuntimeError("The Apple public name differs from approved Ultimate Tic/Tac/Toe")
    versions = all_pages(f"/v1/apps/{APP}/appStoreVersions", {"limit": 30})
    drafts = [v for v in versions if v["attributes"].get("platform") == "IOS" and
              v["attributes"].get("appStoreState") in
              ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED")]
    if not drafts:
        print("No editable iOS version; listing saved in GitHub, version text not applied")
        return
    target = drafts[0]
    locs = all_pages(f"/v1/appStoreVersions/{target['id']}/appStoreVersionLocalizations")
    attrs = {
        "description": LISTING["description"], "keywords": LISTING["keywords"],
        "promotionalText": LISTING["promotionalText"],
        "supportUrl": LISTING["supportUrl"]
    }
    loc = next((x for x in locs if x["attributes"].get("locale") == "en-US"), None)
    if loc:
        changed = {k: v for k, v in attrs.items() if loc["attributes"].get(k) != v}
        if changed:
            query("PATCH", f"/v1/appStoreVersionLocalizations/{loc['id']}", {
                "data": {"type": "appStoreVersionLocalizations", "id": loc["id"], "attributes": changed}
            })
            print("Saved app version description, keywords, support URL")
        else:
            print("App version listing already matches")
    else:
        query("POST", "/v1/appStoreVersionLocalizations", {
            "data": {"type": "appStoreVersionLocalizations",
                     "attributes": {"locale": "en-US", **attrs},
                     "relationships": {"appStoreVersion": ref("appStoreVersions", target["id"])}}
        })
        print("Created en-US app version listing")

def badge_png(title, label, online):
    canvas = Image.new("RGB", (1024, 1024), (11, 19, 31))
    draw = ImageDraw.Draw(canvas)
    accent = (255, 174, 134) if online else (110, 223, 216)
    draw.rounded_rectangle((80, 80, 944, 944), radius=166,
                           fill=(26, 45, 61), outline=accent, width=22)
    # A small 3x3 board motif, not borrowed achievement artwork.
    for i in (1, 2):
        at = 320 + i * 128
        draw.line((448, at, 704, at), fill=(114, 143, 159), width=10)
        draw.line((at, 448, at, 704), fill=(114, 143, 159), width=10)
    for x, y in ((450, 450), (706, 706)):
        draw.line((x-45, y-45, x+45, y+45), fill=accent, width=20)
        draw.line((x-45, y+45, x+45, y-45), fill=accent, width=20)
    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    font = ImageFont.truetype(font_path, 200)
    small_font = ImageFont.truetype(font_path, 40)
    draw.text((512, 260), label, font=font, anchor="mm", fill=accent)
    draw.text((512, 800), title.upper(), font=small_font, anchor="mm",
              fill=(236, 245, 247))
    buffer = io.BytesIO()
    canvas.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()

def ensure_badge(localization, suffix, title, symbol, online):
    route = f"/v1/gameCenterAchievementLocalizations/{localization}/gameCenterAchievementImage"
    old = session.get(BASE + route, timeout=40)
    if old.status_code == 200 and old.json().get("data"):
        print("  Badge already present:", suffix)
        return
    if old.status_code not in (200, 404):
        raise RuntimeError(f"Read achievement image HTTP {old.status_code}")
    payload_bytes = badge_png(title, symbol, online)
    image_obj = query("POST", "/v1/gameCenterAchievementImages", {
        "data": {"type": "gameCenterAchievementImages",
                 "attributes": {"fileSize": len(payload_bytes), "fileName": suffix + ".png"},
                 "relationships": {
                     "gameCenterAchievementLocalization": ref("gameCenterAchievementLocalizations", localization)
                 }}
    })["data"]
    ops = image_obj["attributes"].get("uploadOperations")
    if not ops:
        ops = get(f"/v1/gameCenterAchievementImages/{image_obj['id']}")["attributes"].get("uploadOperations", [])
    if not ops:
        raise RuntimeError("Apple did not return achievement badge upload URLs")
    for op in ops:
        address = op.get("url", "")
        uri = urlparse(address)
        if uri.scheme != "https" or not uri.hostname or uri.username or uri.password:
            raise RuntimeError("Unsafe Apple upload destination")
        offset = int(op.get("offset", 0))
        length = int(op.get("length", len(payload_bytes)-offset))
        if offset < 0 or length <= 0 or offset + length > len(payload_bytes):
            raise RuntimeError("Invalid Apple upload range")
        head = {h["name"]: h["value"] for h in op.get("requestHeaders", [])}
        response = requests.request(op.get("method", "PUT"), address,
                                    data=payload_bytes[offset:offset+length], headers=head, timeout=75)
        if response.status_code >= 300:
            raise RuntimeError(f"Apple badge upload HTTP {response.status_code}")
    query("PATCH", f"/v1/gameCenterAchievementImages/{image_obj['id']}", {
        "data": {"type": "gameCenterAchievementImages",
                 "id": image_obj["id"], "attributes": {"uploaded": True}}
    })
    print("  Generated and uploaded 1024px badge:", suffix)

def configure_game_center():
    detail = get(f"/v1/apps/{APP}/gameCenterDetail")
    if not detail:
        detail = query("POST", "/v1/gameCenterDetails", {
            "data": {"type": "gameCenterDetails",
                     "relationships": {"app": ref("apps", APP)}}
        })["data"]
        print("Created App Store Connect Game Center detail")
    gid = detail["id"]
    boards = all_pages(f"/v1/gameCenterDetails/{gid}/gameCenterLeaderboards")
    lid = "com.killjoy00.ultttt.online_wins"
    board = next((x for x in boards if x["attributes"].get("vendorIdentifier") == lid), None)
    if not board:
        board = query("POST", "/v1/gameCenterLeaderboards", {
            "data": {"type": "gameCenterLeaderboards",
                     "attributes": {
                         "referenceName": "Online Wins", "vendorIdentifier": lid,
                         "defaultFormatter": "INTEGER", "submissionType": "BEST_SCORE",
                         "scoreSortType": "DESC", "scoreRangeStart": "0",
                         "scoreRangeEnd": "1000000"
                     }, "relationships": {"gameCenterDetail": ref("gameCenterDetails", gid)}}
        })["data"]
        print("Created classic Online Wins leaderboard")
    else:
        print("Online Wins leaderboard exists")
    lb_loc = all_pages(f"/v1/gameCenterLeaderboards/{board['id']}/localizations")
    if not any(x["attributes"].get("locale") == "en-US" for x in lb_loc):
        query("POST", "/v1/gameCenterLeaderboardLocalizations", {
            "data": {"type": "gameCenterLeaderboardLocalizations",
                     "attributes": {"locale": "en-US", "name": "Online Wins",
                                    "description": "Most online matches won."},
                     "relationships": {"gameCenterLeaderboard": ref("gameCenterLeaderboards", board["id"])}}
        })
        print("Created Online Wins localization")
    saved = all_pages(f"/v1/gameCenterDetails/{gid}/gameCenterAchievements")
    lookup = {x["attributes"].get("vendorIdentifier"): x for x in saved}
    for suffix, name, points, before, after, symbol in ACHIEVEMENTS:
        vendor = "com.killjoy00.ultttt." + suffix
        achievement = lookup.get(vendor)
        if achievement is None:
            achievement = query("POST", "/v1/gameCenterAchievements", {
                "data": {"type": "gameCenterAchievements",
                         "attributes": {
                             "referenceName": name, "vendorIdentifier": vendor,
                             "points": points, "repeatable": False, "showBeforeEarned": True
                         }, "relationships": {"gameCenterDetail": ref("gameCenterDetails", gid)}}
            })["data"]
            print("Created achievement", vendor)
        else:
            if int(achievement["attributes"].get("points", -1)) != points:
                raise RuntimeError(f"Achievement points mismatch for {vendor}; refused to alter")
            print("Achievement already exists", vendor)
        locs = all_pages(f"/v1/gameCenterAchievements/{achievement['id']}/localizations")
        loc = next((x for x in locs if x["attributes"].get("locale") == "en-US"), None)
        if not loc:
            loc = query("POST", "/v1/gameCenterAchievementLocalizations", {
                "data": {"type": "gameCenterAchievementLocalizations",
                         "attributes": {"locale": "en-US", "name": name,
                                        "beforeEarnedDescription": before,
                                        "afterEarnedDescription": after},
                         "relationships": {"gameCenterAchievement": ref("gameCenterAchievements", achievement["id"])}}
            })["data"]
        ensure_badge(loc["id"], suffix, name, symbol, "online" in suffix)

def configure_iap():
    product_id = "com.killjoy00.ultttt.remove_ads"
    products = all_pages(f"/v1/apps/{APP}/inAppPurchasesV2", {"limit": 100})
    product = next((x for x in products if x["attributes"].get("productId") == product_id), None)
    if not product:
        product = query("POST", "/v2/inAppPurchases", {
            "data": {"type": "inAppPurchases",
                     "attributes": {
                         "name": "Remove Ads",
                         "productId": product_id,
                         "inAppPurchaseType": "NON_CONSUMABLE",
                         "familySharable": False
                     }, "relationships": {"app": ref("apps", APP)}}
        })["data"]
        print("Created draft non-consumable Remove Ads IAP")
    else:
        print("Remove Ads IAP exists")
        if product["attributes"].get("inAppPurchaseType") != "NON_CONSUMABLE":
            raise RuntimeError("Remove Ads exists but is not NON_CONSUMABLE")
    versions = all_pages(f"/v2/inAppPurchases/{product['id']}/versions")
    draft = next((x for x in versions if x["attributes"].get("state") == "PREPARE_FOR_SUBMISSION"), None)
    if draft is None and not versions:
        draft = query("POST", "/v1/inAppPurchaseVersions", {
            "data": {"type": "inAppPurchaseVersions",
                     "relationships": {"inAppPurchase": ref("inAppPurchases", product["id"])}}
        })["data"]
        print("Created draft in-app purchase version")
    if not draft:
        print("No editable IAP version; Apple retains previously configured localizations")
    else:
        locs = all_pages(f"/v1/inAppPurchaseVersions/{draft['id']}/localizations")
        if not any(x["attributes"].get("locale") == "en-US" for x in locs):
            query("POST", "/v2/inAppPurchaseLocalizations", {
                "data": {"type": "inAppPurchaseLocalizations",
                         "attributes": {
                             "locale": "en-US", "name": "Remove Ads",
                             "description": "Remove banner ads with one purchase."
                         }, "relationships": {"version": ref("inAppPurchaseVersions", draft["id"])}}
            })
            print("Created Remove Ads en-US localization")
    print("IAP price and review screenshot still require a release decision.")

failures = []
for label, job in [
    ("App Store listing", configure_listing),
    ("Game Center", configure_game_center),
    ("Remove Ads draft", configure_iap),
]:
    try:
        print("\n==", label, "==")
        job()
    except Exception as exc:
        print("::error::" + label + ": " + str(exc))
        failures.append(label)
if failures:
    raise SystemExit("Configuration partially completed. Rerun after fixing: " + ", ".join(failures))
print("\nApp draft metadata, Game Center and IAP prepared. Nothing submitted to review.")
