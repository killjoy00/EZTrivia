#!/usr/bin/env python3
"""Stage approved EZ Trivia iOS 1.4 assets with the 2026 App Asset Library API.

This uploads to the existing editable 1.4 en-US App Store product-page
localization. It does not submit for review, publish, remove existing
screenshots, or touch any Android listing.
"""
from __future__ import annotations

import base64
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

import jwt
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "artwork_generated"
APP_BUNDLE = "com.rsm.eztrivia"
VERSION = "1.4"
LOCALE = "en-US"
API_ROOT = "https://api.appstoreconnect.apple.com"
IPHONE_GROUP = "IPHONE_DYNAMIC_ISLAND_LARGE_PROFILE"

ARTWORK = [
    ("EZTrivia_product_header_3840x1646.png", "PRODUCT_PAGE_HEADER_ASSET",
     "CREATIVE_ASSETS", "EZ Trivia 1.4 | Product page header"),
    ("EZTrivia_search_results_1920x1280.png", "APP_STORE_SEARCH_RESULTS_ASSET",
     "CREATIVE_ASSETS", "EZ Trivia 1.4 | Search results"),
    ("EZTrivia_screenshot_home_1320x2868.png", "APP_SCREENSHOT",
     "APP_SCREENSHOTS_AND_PREVIEWS", "EZ Trivia 1.4 | Screenshot 1 Home"),
    ("EZTrivia_screenshot_daily_1320x2868.png", "APP_SCREENSHOT",
     "APP_SCREENSHOTS_AND_PREVIEWS", "EZ Trivia 1.4 | Screenshot 2 Daily"),
    ("EZTrivia_screenshot_explanation_1320x2868.png", "APP_SCREENSHOT",
     "APP_SCREENSHOTS_AND_PREVIEWS", "EZ Trivia 1.4 | Screenshot 3 Explanation"),
]


def private_key():
    raw = os.environ.get("ASC_KEY_P8", "").strip().replace("\\n", "\n").replace("\r", "")
    if "BEGIN PRIVATE KEY" not in raw:
        try:
            raw = base64.b64decode(raw).decode("utf-8")
        except Exception:
            pass
    if "BEGIN PRIVATE KEY" not in raw:
        raw = "-----BEGIN PRIVATE KEY-----\n" + raw + "\n-----END PRIVATE KEY-----\n"
    return raw


KEY = private_key()
ISSUER = os.environ["ASC_ISSUER_ID"]
KEY_ID = os.environ["ASC_KEY_ID"]


def token():
    now = int(time.time())
    return jwt.encode(
        {"iss": ISSUER, "iat": now, "exp": now + 900, "aud": "appstoreconnect-v1"},
        KEY, algorithm="ES256", headers={"kid": KEY_ID}
    )


def request(method, path, body=None):
    url = path if path.startswith("https://") else API_ROOT + path
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Authorization": "Bearer " + token(), "Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    for attempt in range(4):
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as response:
                raw = response.read()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")[:1400]
            if error.code in (429, 500, 502, 503) and attempt < 3:
                time.sleep(4 * (attempt + 1))
                continue
            raise RuntimeError(f"App Store Connect {method} {path} -> HTTP {error.code}: {detail}") from error


def all_items(path):
    results = []
    page = path
    while page:
        data = request("GET", page)
        results.extend(data.get("data", []))
        page = data.get("links", {}).get("next")
    return results


def rel(type_name, identifier):
    return {"data": {"type": type_name, "id": identifier}}


def image_dimensions(path):
    with Image.open(path) as im:
        if im.format != "PNG" or im.mode not in ("RGB", "P"):
            raise RuntimeError(f"Artwork must be opaque PNG: {path}")
        return im.size


def valid_spec(spec, dims):
    d = spec.get("dimensions", {})
    return (d.get("minWidth", 10**9) <= dims[0] <= d.get("maxWidth", 0)
            and d.get("minHeight", 10**9) <= dims[1] <= d.get("maxHeight", 0)
            and ".png" in spec.get("fileExtensions", []))


def group_options(refdata, placement_type, dims, category):
    ref_types = {x["placementTypeId"]: x for x in refdata["placementTypes"]}
    record = ref_types.get(placement_type)
    if not record:
        raise RuntimeError(f"Apple reference data doesn't support {placement_type}")
    if category not in record.get("acceptsAssetCategories", []):
        raise RuntimeError(f"Invalid asset category {category} for {placement_type}")
    specs = {x["specId"]: x for x in refdata["imageSpecs"]}
    options = []
    for mapping in record.get("specMappings", []):
        if any(valid_spec(specs.get(sid, {}), dims) for sid in mapping.get("specs", [])):
            options.append(mapping["placementGroupId"])
    if not options:
        raise RuntimeError(f"No Apple asset specification accepts {placement_type} {dims}")
    features = next(x for x in refdata["features"] if x["featureId"] == "APP_STORE_VERSIONS")
    policy = next(x for x in features["placementPolicies"] if x["placementType"] == placement_type)
    allowed = {g for rule in policy["groupLimits"] for g in rule["groupIds"]}
    return [g for g in options if g in allowed]


def choose_creative_groups(groups, refdata):
    # The header/search images can back multiple Apple device placements.
    # Favor the current largest iPhone and iPad profiles, or a universal group.
    lookup = {x["placementProfileGroupId"]: x for x in refdata["placementProfileGroups"]}
    universal = [g for g in groups if lookup.get(g, {}).get("platform") == "ANY"]
    if universal:
        return universal[:1]
    selected = []
    for platform, preferred in (
        ("IPHONE_APP_STORE", "IPHONE_DYNAMIC_ISLAND_LARGE_PROFILE"),
        ("IPAD_APP_STORE", "IPAD_13_PROFILE")
    ):
        choices = [g for g in groups if lookup.get(g, {}).get("platform") == platform]
        if choices:
            selected.append(preferred if preferred in choices else choices[0])
    if selected:
        return list(dict.fromkeys(selected))
    if groups:
        return groups[:1]
    raise RuntimeError("No creative placement groups are valid on this iOS listing")


def existing_placements(localization_id):
    query = urllib.parse.urlencode({"limit": 200, "sort": "placementGroupPosition"})
    return all_items("/v1/appStoreVersionLocalizations/" + localization_id + "/placements?" + query)


def upload_part(op, payload):
    offset = op["offset"]
    length = op["length"]
    chunk = payload[offset:offset + length]
    if len(chunk) != length:
        raise RuntimeError("Invalid upload operation length")
    headers = {item["name"]: item["value"] for item in op.get("requestHeaders", [])}
    req = urllib.request.Request(op["url"], data=chunk, method=op["method"], headers=headers)
    with urllib.request.urlopen(req, timeout=120) as response:
        response.read()


def stage_image(name, category, reference_name, library_id, cache):
    existing = [
        row for row in cache if row.get("attributes", {}).get("referenceName") == reference_name
        and row.get("attributes", {}).get("state") in ("PREPARE_FOR_SUBMISSION", "APPROVED")
    ]
    if existing:
        asset_id = existing[0]["id"]
        print("REUSE approved/staged image", reference_name, asset_id, flush=True)
        return asset_id
    path = ART / name
    payload = path.read_bytes()
    reserve = request("POST", "/v1/appAssetLibraryImages", {
        "data": {
            "type": "appAssetLibraryImages",
            "attributes": {
                "fileName": name,
                "fileSize": len(payload),
                "category": category,
                "referenceName": reference_name,
            },
            "relationships": {"assetLibrary": rel("appAssetLibraries", library_id)},
        }
    })
    asset_id = reserve["data"]["id"]
    print("RESERVED image", reference_name, asset_id, flush=True)
    for operation in reserve["data"]["attributes"].get("uploadOperations", []):
        upload_part(operation, payload)
    request("PATCH", "/v1/appAssetLibraryImages/" + asset_id, {
        "data": {
            "type": "appAssetLibraryImages",
            "id": asset_id,
            "attributes": {"uploaded": True},
        }
    })
    for _ in range(40):
        status = request("GET", "/v1/appAssetLibraryImages/" + asset_id)["data"]["attributes"]
        state = status.get("state")
        if state in ("PREPARE_FOR_SUBMISSION", "APPROVED"):
            print("READY image", reference_name, state, flush=True)
            return asset_id
        if state in ("FAILED", "REJECTED"):
            raise RuntimeError(f"Apple asset processing failed for {reference_name}: {status.get('stateDetails')}")
        time.sleep(4)
    raise RuntimeError(f"Apple asset processing timed out for {reference_name}; id={asset_id}")


def placement_asset_id(placement):
    return ((placement.get("relationships", {}).get("image") or {}).get("data") or {}).get("id")


def create_placement(asset_id, placement_type, group, localization_id):
    result = request("POST", "/v1/appAssetLibraryPlacements", {
        "data": {
            "type": "appAssetLibraryPlacements",
            "attributes": {"placementType": placement_type, "placementGroup": group},
            "relationships": {
                "image": rel("appAssetLibraryImages", asset_id),
                "appStoreVersionLocalization": rel("appStoreVersionLocalizations", localization_id),
            },
        }
    })
    placement_id = result["data"]["id"]
    print("PLACED", placement_type, group, asset_id, "as", placement_id, flush=True)
    return placement_id


def main():
    print("Target:", APP_BUNDLE, "iOS App Store", VERSION, LOCALE)
    checks = {filename: image_dimensions(ART / filename) for filename, _, _, _ in ARTWORK}

    apps = all_items("/v1/apps?" + urllib.parse.urlencode({"filter[bundleId]": APP_BUNDLE, "limit": 100}))
    if len(apps) != 1:
        raise RuntimeError("Expected one EZ Trivia bundle ID; no changes made")
    app_id = apps[0]["id"]
    versions = all_items("/v1/apps/" + app_id + "/appStoreVersions?limit=200")
    matching = [
        x for x in versions if x["attributes"].get("platform") == "IOS"
        and x["attributes"].get("versionString") in (VERSION, VERSION + ".0")
    ]
    if len(matching) != 1:
        raise RuntimeError("iOS 1.4 version missing or ambiguous; no changes made")
    version = matching[0]
    state = version["attributes"]["appStoreState"]
    print("Found version:", version["id"], state)
    if state != "PREPARE_FOR_SUBMISSION":
        raise RuntimeError(f"iOS {VERSION} isn't editable ({state}); no changes made")
    localizations = all_items("/v1/appStoreVersions/" + version["id"] + "/appStoreVersionLocalizations?limit=200")
    localized = [x for x in localizations if x["attributes"].get("locale") == LOCALE]
    if len(localized) != 1:
        raise RuntimeError(f"Expected one {LOCALE} localization; no changes made")
    localization_id = localized[0]["id"]
    library_id = request("GET", "/v1/apps/" + app_id + "/assetLibrary")["data"]["id"]

    ref_response = request("GET", "/v1/appAssetLibraryRefData")
    refdata = ref_response["data"][0]["attributes"]
    chosen = {}
    for name, placement_type, category, reference_name in ARTWORK:
        options = group_options(refdata, placement_type, checks[name], category)
        groups = ([IPHONE_GROUP] if placement_type == "APP_SCREENSHOT"
                  and IPHONE_GROUP in options else choose_creative_groups(options, refdata)
                  if placement_type != "APP_SCREENSHOT" else [])
        if not groups:
            raise RuntimeError("No supported iPhone screenshot profile group; no changes made")
        chosen[name] = (placement_type, category, reference_name, groups)
        print("VALIDATED", name, checks[name], "groups:", groups, flush=True)

    before = existing_placements(localization_id)
    iphone = [
        x for x in before if x["attributes"].get("placementType") == "APP_SCREENSHOT"
        and x["attributes"].get("placementGroup") == IPHONE_GROUP
    ]
    print("Existing large iPhone screenshots:", len(iphone))
    # The approved lineup has 7 existing captures. Append up to 3 without
    # deleting or altering them, and then make the three new assets first.
    if len(iphone) > 10:
        raise RuntimeError("More than 10 existing screenshots; aborting before any upload")
    if len(iphone) < 7:
        print("NOTE: Fewer than seven screenshots; preserving existing order and data.")
    cache = all_items("/v1/appAssetLibraries/" + library_id + "/images?limit=200")

    uploaded = {}
    for name, _, category, reference_name in ARTWORK:
        uploaded[name] = stage_image(name, category, reference_name, library_id, cache)

    latest = existing_placements(localization_id)
    screenshots_new = []
    original_ids = [x["id"] for x in iphone]

    for name, _, _, _ in ARTWORK:
        placement_type, _, _, groups = chosen[name]
        asset_id = uploaded[name]
        for group in groups:
            matched = [
                row for row in latest if row["attributes"].get("placementType") == placement_type
                and row["attributes"].get("placementGroup") == group
            ]
            already = [row for row in matched if placement_asset_id(row) == asset_id]
            if already:
                placement_id = already[0]["id"]
                print("REUSE placement", placement_type, group, placement_id)
            elif placement_type == "APP_SCREENSHOT":
                if len(matched) >= 10:
                    raise RuntimeError("iPhone screenshot limit reached before placing " + name)
                placement_id = create_placement(asset_id, placement_type, group, localization_id)
                latest = existing_placements(localization_id)
            else:
                if matched:
                    raise RuntimeError(f"Existing {placement_type}/{group} found; will not overwrite")
                placement_id = create_placement(asset_id, placement_type, group, localization_id)
                latest = existing_placements(localization_id)
            if placement_type == "APP_SCREENSHOT":
                screenshots_new.append(placement_id)

    if screenshots_new:
        current = existing_placements(localization_id)
        original = [
            row["id"] for row in current
            if row["attributes"].get("placementType") == "APP_SCREENSHOT"
            and row["attributes"].get("placementGroup") == IPHONE_GROUP
            and row["id"] not in screenshots_new
        ]
        ordered = screenshots_new + original
        if len(ordered) > 10 or len(ordered) != len(set(ordered)):
            raise RuntimeError(f"Bad iPhone screenshot ordering request, count={len(ordered)}")
        request("POST", "/v1/appAssetLibraryPlacementOrderingRequests", {
            "data": {
                "type": "appAssetLibraryPlacementOrderingRequests",
                "attributes": {"placementGroup": IPHONE_GROUP},
                "relationships": {
                    "orderedPlacements": {"data": [
                        {"type": "appAssetLibraryPlacements", "id": pid}
                        for pid in ordered
                    ]},
                    "appStoreVersionLocalization": rel("appStoreVersionLocalizations", localization_id),
                },
            }
        })
        print("ORDERED new iPhone screenshots first:", screenshots_new)

    final = existing_placements(localization_id)
    print("Final iOS 1.4 English placements:",
          [(x["attributes"].get("placementType"), x["attributes"].get("placementGroup"))
           for x in final], flush=True)
    print("Assets staged on iOS 1.4. No App Store review submission or publication performed.")


if __name__ == "__main__":
    main()
