#!/usr/bin/env python3
"""Ensure Ministry has an internal TestFlight group using shared EZTrivia Apple credentials."""
import base64
import os
import time
from urllib.parse import urlencode
import jwt
import requests

BUNDLE = "org.killjoy00.ministry"
APP_ID = "6836844590"
BASE = "https://api.appstoreconnect.apple.com/v1"

def key():
    v=os.environ["ASC_KEY_P8"].replace("\r","").strip()
    if "BEGIN PRIVATE KEY" not in v:
        if "\\n" in v:
            v=v.replace("\\n","\n")
        else:
            try:
                decoded=base64.b64decode(v).decode()
                if "BEGIN PRIVATE KEY" in decoded:v=decoded
            except Exception:pass
    if "BEGIN PRIVATE KEY" not in v:
        v="-----BEGIN PRIVATE KEY-----\n"+"\n".join(v[i:i+64] for i in range(0,len(v),64))+"\n-----END PRIVATE KEY-----\n"
    return v

now=int(time.time())
token=jwt.encode({"iss":os.environ["ASC_ISSUER_ID"],"iat":now,"exp":now+600,"aud":"appstoreconnect-v1"},key(),algorithm="ES256",headers={"kid":os.environ["ASC_KEY_ID"]})
s=requests.Session()
s.headers.update({"Authorization":"Bearer "+token,"Accept":"application/json","Content-Type":"application/json"})

def api(method,path,payload=None,params=None,optional=False):
    r=s.request(method,BASE+path,json=payload,params=params,timeout=45)
    if not 200<=r.status_code<300:
        if optional and r.status_code in (403,404):
            return None
        try: details="; ".join(x.get("detail","") for x in r.json().get("errors",[])[:2])
        except Exception: details=r.text[:200]
        raise RuntimeError(f"Apple {method} {path}: HTTP {r.status_code} {details[:250]}")
    return r.json() if r.content else {}

def rows(path,params=None,optional=False):
    data=[]
    while path:
        res=api("GET",path,params=params,optional=optional)
        if res is None:return None
        data += res.get("data",[])
        path=res.get("links",{}).get("next")
        if path and path.startswith(BASE):path=path[len(BASE):]
        params=None
    return data

apps=rows("/apps",{"filter[bundleId]":BUNDLE,"limit":5})
assert len(apps)==1 and apps[0]["id"]==APP_ID and apps[0]["attributes"]["name"]=="The Ministry of Coincidences", "Wrong Apple app"
builds=rows("/builds",{"filter[app]":APP_ID,"sort":"-uploadedDate","limit":20})
print("Ministry TestFlight builds",[(b["attributes"].get("version"),b["attributes"].get("processingState")) for b in builds[:4]])
valid=[b for b in builds if b["attributes"].get("processingState")=="VALID"]
assert valid, "No processed valid Ministry TestFlight build yet"
groups=rows(f"/apps/{APP_ID}/betaGroups",{"limit":100})
group=next((g for g in groups if g["attributes"].get("isInternalGroup") is True and g["attributes"].get("name")=="Internal Testing"),None)
if not group:
    group=api("POST","/betaGroups",{"data":{"type":"betaGroups","attributes":{"name":"Internal Testing","isInternalGroup":True,"hasAccessToAllBuilds":True},"relationships":{"app":{"data":{"type":"apps","id":APP_ID}}}}})["data"]
    print("Created Ministry Internal Testing group")
elif not group["attributes"].get("hasAccessToAllBuilds"):
    api("PATCH","/betaGroups/"+group["id"],{"data":{"type":"betaGroups","id":group["id"],"attributes":{"hasAccessToAllBuilds":True}}})
    print("Enabled automatic access to future Ministry builds")
else:
    print("Ministry Internal Testing group already has access to all builds")
group=api("GET","/betaGroups/"+group["id"])["data"]
assert group["attributes"].get("isInternalGroup") and group["attributes"].get("hasAccessToAllBuilds")
gid=group["id"]
testers=rows(f"/betaGroups/{gid}/betaTesters",{"limit":100},optional=True) or []
if not testers:
    users=rows("/users",{"filter[roles]":"ACCOUNT_HOLDER","limit":10},optional=True)
    if users and len(users)==1:
        p=users[0]["attributes"]
        email=p.get("username","")
        if "@" in email:
            try:
                known=rows("/betaTesters",{"filter[email]":email,"limit":10},optional=True)
                if known:
                    api("POST",f"/betaGroups/{gid}/relationships/betaTesters",{"data":[{"type":"betaTesters","id":known[0]["id"]}]})
                elif known is not None:
                    api("POST","/betaTesters",{"data":{"type":"betaTesters","attributes":{"email":email,"firstName":p.get("firstName") or "","lastName":p.get("lastName") or ""},"relationships":{"betaGroups":{"data":[{"type":"betaGroups","id":gid}]},"apps":{"data":[{"type":"apps","id":APP_ID}]}}}})
                print("Added or invited Apple account holder to Ministry Internal Testing")
            except Exception as e:
                print("Account-holder invitation not permitted by Apple API:",str(e)[:160])
testers=rows(f"/betaGroups/{gid}/betaTesters",{"limit":100},optional=True) or []
print("VERIFIED Ministry internal beta group",gid,"tester count",len(testers),"valid build",valid[0]["attributes"].get("version"))
