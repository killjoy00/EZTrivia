#!/usr/bin/env python3
"""Print targeted rows from Google's current Play Data safety CSV template."""

from __future__ import annotations

import csv
import io
import requests

URL = "https://storage.googleapis.com/support-kms-prod/b5v9It2EgwrgyY1gPFVB3jPUypc5lL3oNg2G"
response = requests.get(URL, timeout=60)
response.raise_for_status()
rows = list(csv.DictReader(io.StringIO(response.content.decode("utf-8-sig"))))
print(f"rows={len(rows)}")

needles = (
    "ACCOUNT_CREATION",
    "DELETE",
    "DELETION",
    "ACCOUNT",
)
for index, row in enumerate(rows, start=2):
    qid = row.get("Question ID (machine readable)", "") or ""
    rid = row.get("Response ID (machine readable)", "") or ""
    label = row.get("Human-friendly question label", "") or ""
    haystack = f"{qid} {rid} {label}".upper()
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
