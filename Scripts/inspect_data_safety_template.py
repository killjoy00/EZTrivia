#!/usr/bin/env python3
"""Print the current Google Play Data safety CSV header/top-level rows."""

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
print(f"headers={list(rows[0].keys()) if rows else []}")

for index, row in enumerate(rows[:41], start=2):
    print(
        "ROW",
        index,
        repr(row.get("Question ID (machine readable)", "")),
        repr(row.get("Response ID (machine readable)", "")),
        repr(row.get("Response value", "")),
        repr(row.get("Answer requirement", "")),
        repr(row.get("Human-friendly question label", "")),
    )
