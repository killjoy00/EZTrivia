#!/usr/bin/env python3
import csv
import html
import io
import re
import requests

HELP = "https://support.google.com/googleplay/android-developer/answer/10787469?hl=en"
CURRENT_SAMPLE_FALLBACK = "https://storage.googleapis.com/support-kms-prod/b5v9It2EgwrgyY1gPFVB3jPUypc5lL3oNg2G"
page = requests.get(HELP, timeout=60)
page.raise_for_status()
match = re.search(r'https://storage\.googleapis\.com/support-kms-prod/[^"<>& ]+', page.text)
url = html.unescape(match.group(0)) if match else CURRENT_SAMPLE_FALLBACK
print("template_url=", url)
response = requests.get(url, timeout=60)
print("download_status=", response.status_code, "content_type=", response.headers.get("content-type"))
response.raise_for_status()
text = response.content.decode("utf-8-sig")
rows = list(csv.DictReader(io.StringIO(text)))
print("rows=", len(rows))
print("headers=", list(rows[0].keys()) if rows else [])

wanted_codes = {
    "PSL_APPROX_LOCATION",
    "PSL_USER_INTERACTION",
    "PSL_PERFORMANCE_DIAGNOSTICS",
    "PSL_DEVICE_ID",
    "PSL_USER_ACCOUNT",
    "PSL_OTHER_APP_ACTIVITY",
    "PSL_PHOTOS",
}
for row in rows:
    q = row.get("Question ID (machine readable)", "")
    r = row.get("Response ID (machine readable)", "")
    if (
        q.startswith("PSL_DATA_COLLECTION")
        or q.startswith("PSL_SUPPORTED_ACCOUNT")
        or q.startswith("PSL_OUTSIDE_APP_ACCOUNT")
        or q.startswith("PSL_ACCOUNT_")
        or r in wanted_codes
        or any(f":{code}:" in q for code in wanted_codes)
    ):
        print("ROW", repr(row))
