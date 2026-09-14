#!/usr/bin/env python3
"""Print the current Google Play Data safety CSV rows relevant to EZ Trivia."""

from __future__ import annotations

import csv
import html
import io
import re
import requests

HELP = "https://support.google.com/googleplay/android-developer/answer/10787469?hl=en"
FALLBACK = "https://storage.googleapis.com/support-kms-prod/b5v9It2EgwrgyY1gPFVB3jPUypc5lL3oNg2G"

page = requests.get(HELP, timeout=60)
page.raise_for_status()
match = re.search(r'https://storage\.googleapis\.com/support-kms-prod/[^\"<>& ]+', page.text)
url = html.unescape(match.group(0)) if match else FALLBACK
print(f"template_url={url}")
response = requests.get(url, timeout=60)
response.raise_for_status()
text = response.content.decode("utf-8-sig")
rows = list(csv.DictReader(io.StringIO(text)))
print(f"rows={len(rows)}")

needles = (
    "data collection and security",
    "approximate location",
    "app interactions",
    "other actions",
    "diagnostics",
    "device or other ids",
    "email address",
    "user ids",
    "purchase history",
    "other financial info",
    "files and docs",
    "other user-generated content",
    "collected, shared, or both",
    "processed ephemerally",
    "required for your app",
    "why is this user data collected",
    "why is this user data shared",
    "encrypted in transit",
    "request that their data is deleted",
)

for index, row in enumerate(rows, start=2):
    label = row.get("Human-friendly question label", "") or ""
    qid = row.get("Question ID (machine readable)", "") or ""
    rid = row.get("Response ID (machine readable)", "") or ""
    haystack = f"{label} {qid} {rid}".lower()
    if any(needle in haystack for needle in needles):
        print(
            "ROW",
            index,
            repr(qid),
            repr(rid),
            repr(row.get("Response value", "")),
            repr(row.get("Answer requirement", "")),
            repr(label),
        )
