#!/usr/bin/env python3
"""Build and optionally submit EZ Trivia's Google Play Data safety declaration.

The declaration is generated from Google's current CSV template.  We blank all
sample answers first, then apply only the answers justified by the Android app
and its bundled Google SDKs.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import os
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent.parent
PACKAGE_NAME = "com.rsm.eztrivia"
OUTPUT = ROOT / "play-data-safety" / "eztrivia-data-safety.csv"
TEMPLATE_URL = os.environ.get(
    "DATA_SAFETY_TEMPLATE_URL",
    "https://storage.googleapis.com/support-kms-prod/b5v9It2EgwrgyY1gPFVB3jPUypc5lL3oNg2G",
)
API_URL = (
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
    f"{PACKAGE_NAME}/dataSafety"
)

Q = "Question ID (machine readable)"
R = "Response ID (machine readable)"
V = "Response value"

# Google Play Data safety purposes.
APP_FUNCTIONALITY = "PSL_APP_FUNCTIONALITY"
ANALYTICS = "PSL_ANALYTICS"
FRAUD = "PSL_FRAUD_PREVENTION_SECURITY"
ADVERTISING = "PSL_ADVERTISING"

# Data types disclosed by EZ Trivia's current Android integration.
#
# Google Mobile Ads 25.4.0 automatically collects and shares IP-derived
# approximate location, user-product interactions, diagnostics, and device /
# account identifiers for advertising, analytics, and fraud prevention.
# Google Play Games is optional and handles gamer identity plus the scores,
# achievements, and Saved Games progress that EZ Trivia sends to the service.
# Google Play Billing is optional; EZ Trivia receives product ownership and
# returns the purchase token to Play when acknowledging Remove Ads. No payment
# card/bank data or developer-run purchase backend is used.
DECLARATIONS = {
    # data code: collected, shared, required, collection purposes, sharing purposes
    "PSL_APPROX_LOCATION": (
        True,
        True,
        True,
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD, ADVERTISING},
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD, ADVERTISING},
    ),
    "PSL_USER_INTERACTION": (
        True,
        True,
        True,
        {ANALYTICS, FRAUD, ADVERTISING},
        {ANALYTICS, FRAUD, ADVERTISING},
    ),
    "PSL_PERFORMANCE_DIAGNOSTICS": (
        True,
        True,
        True,
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD, ADVERTISING},
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD, ADVERTISING},
    ),
    "PSL_DEVICE_ID": (
        True,
        True,
        True,
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD, ADVERTISING},
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD, ADVERTISING},
    ),
    "PSL_NAME": (
        True,
        True,
        False,
        {APP_FUNCTIONALITY},
        {APP_FUNCTIONALITY},
    ),
    "PSL_EMAIL": (
        True,
        True,
        False,
        {APP_FUNCTIONALITY},
        {APP_FUNCTIONALITY},
    ),
    "PSL_USER_ACCOUNT": (
        True,
        True,
        False,
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD, ADVERTISING},
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD, ADVERTISING},
    ),
    "PSL_OTHER_APP_ACTIVITY": (
        True,
        True,
        False,
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD},
        {APP_FUNCTIONALITY, ANALYTICS, FRAUD},
    ),
    "PSL_PURCHASE_HISTORY": (
        True,
        False,
        False,
        {APP_FUNCTIONALITY},
        set(),
    ),
}

DATA_TYPE_QUESTIONS = {
    "PSL_NAME": "PSL_DATA_TYPES_PERSONAL",
    "PSL_EMAIL": "PSL_DATA_TYPES_PERSONAL",
    "PSL_USER_ACCOUNT": "PSL_DATA_TYPES_PERSONAL",
    "PSL_PURCHASE_HISTORY": "PSL_DATA_TYPES_FINANCIAL",
    "PSL_APPROX_LOCATION": "PSL_DATA_TYPES_LOCATION",
    "PSL_PERFORMANCE_DIAGNOSTICS": "PSL_DATA_TYPES_APP_PERFORMANCE",
    "PSL_USER_INTERACTION": "PSL_DATA_TYPES_APP_ACTIVITY",
    "PSL_OTHER_APP_ACTIVITY": "PSL_DATA_TYPES_APP_ACTIVITY",
    "PSL_DEVICE_ID": "PSL_DATA_TYPES_IDENTIFIERS",
}


def fetch_template() -> tuple[list[dict[str, str]], list[str]]:
    response = requests.get(TEMPLATE_URL, timeout=60)
    response.raise_for_status()
    reader = csv.DictReader(io.StringIO(response.content.decode("utf-8-sig")))
    rows = [dict(row) for row in reader]
    if not reader.fieldnames:
        raise RuntimeError("Google Data safety template has no header")
    expected = {Q, R, V, "Answer requirement", "Human-friendly question label"}
    if not expected.issubset(reader.fieldnames):
        raise RuntimeError(f"Unexpected Data safety template columns: {reader.fieldnames}")
    if len(rows) < 100:
        raise RuntimeError(f"Unexpectedly short Data safety template: {len(rows)} rows")
    return rows, list(reader.fieldnames)


def index_rows(rows: list[dict[str, str]]) -> dict[tuple[str, str], dict[str, str]]:
    result: dict[tuple[str, str], dict[str, str]] = {}
    for row in rows:
        key = (row.get(Q, ""), row.get(R, ""))
        if key in result:
            raise RuntimeError(f"Duplicate template row: {key}")
        result[key] = row
    return result


def answer(index: dict[tuple[str, str], dict[str, str]], question: str, response: str, value: bool) -> None:
    key = (question, response)
    if key not in index:
        raise RuntimeError(f"Current Google template is missing required row {key}")
    index[key][V] = "TRUE" if value else "FALSE"


def select(index: dict[tuple[str, str], dict[str, str]], question: str, response: str) -> None:
    key = (question, response)
    if key not in index:
        raise RuntimeError(f"Current Google template is missing required choice {key}")
    index[key][V] = "TRUE"


def apply_declaration(rows: list[dict[str, str]]) -> None:
    # Google's downloadable file is a sample and contains example TRUE/FALSE
    # values. Never carry any of those example answers into EZ Trivia's form.
    for row in rows:
        row[V] = ""

    index = index_rows(rows)

    answer(index, "PSL_DATA_COLLECTION_COLLECTS_PERSONAL_DATA", "", True)
    answer(index, "PSL_DATA_COLLECTION_ENCRYPTED_IN_TRANSIT", "", True)

    # EZ Trivia has no developer-run account or user-data backend. Local data can
    # be cleared in-app/uninstalled and Google-managed Play Games data can be
    # deleted using Google's account controls, but there is not a single global
    # developer-operated deletion-request mechanism for every declared SDK data
    # type. Keep this conservative rather than claiming the deletion badge.
    answer(index, "PSL_DATA_COLLECTION_USER_REQUEST_DELETE", "", False)

    for code, (collected, shared, required, collect_purposes, share_purposes) in DECLARATIONS.items():
        select(index, DATA_TYPE_QUESTIONS[code], code)

        handling_q = f"PSL_DATA_USAGE_RESPONSES:{code}:PSL_DATA_USAGE_COLLECTION_AND_SHARING"
        if collected:
            select(index, handling_q, "PSL_DATA_USAGE_ONLY_COLLECTED")
        if shared:
            select(index, handling_q, "PSL_DATA_USAGE_ONLY_SHARED")

        # None of the declared categories are intentionally processed only in
        # memory for the duration of a single request.
        answer(index, f"PSL_DATA_USAGE_RESPONSES:{code}:PSL_DATA_USAGE_EPHEMERAL", "", False)

        control_q = f"PSL_DATA_USAGE_RESPONSES:{code}:DATA_USAGE_USER_CONTROL"
        select(
            index,
            control_q,
            "PSL_DATA_USAGE_USER_CONTROL_REQUIRED" if required else "PSL_DATA_USAGE_USER_CONTROL_OPTIONAL",
        )

        collect_q = f"PSL_DATA_USAGE_RESPONSES:{code}:DATA_USAGE_COLLECTION_PURPOSE"
        for purpose in sorted(collect_purposes):
            select(index, collect_q, purpose)

        if shared:
            share_q = f"PSL_DATA_USAGE_RESPONSES:{code}:DATA_USAGE_SHARING_PURPOSE"
            for purpose in sorted(share_purposes):
                select(index, share_q, purpose)


def render(rows: list[dict[str, str]], fieldnames: list[str]) -> str:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fieldnames, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def validate(rows: list[dict[str, str]]) -> None:
    nonempty = [row for row in rows if row.get(V)]
    if not nonempty:
        raise RuntimeError("Generated declaration has no answers")
    for row in nonempty:
        if row[V] not in {"TRUE", "FALSE"}:
            raise RuntimeError(f"Unexpected response value: {row[V]!r}")

    selected_codes = {
        row[R]
        for row in rows
        if row.get(V) == "TRUE" and row.get(R) in DATA_TYPE_QUESTIONS
    }
    expected_codes = set(DECLARATIONS)
    if selected_codes != expected_codes:
        raise RuntimeError(
            f"Selected data types differ from declaration: got {sorted(selected_codes)}, "
            f"expected {sorted(expected_codes)}"
        )


def submit(csv_text: str, token: str) -> None:
    response = requests.post(
        API_URL,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        data=json.dumps({"safetyLabels": csv_text}),
        timeout=120,
    )
    if not response.ok:
        raise RuntimeError(
            f"Google Play Data safety submission failed: HTTP {response.status_code}: {response.text}"
        )
    print(f"Google Play accepted the Data safety declaration for {PACKAGE_NAME} (HTTP {response.status_code}).")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--submit", action="store_true", help="POST the generated CSV to Google Play")
    args = parser.parse_args()

    rows, fieldnames = fetch_template()
    apply_declaration(rows)
    validate(rows)
    csv_text = render(rows, fieldnames)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(csv_text, encoding="utf-8")

    selected = ", ".join(sorted(DECLARATIONS))
    print(f"Template: {TEMPLATE_URL}")
    print(f"Wrote {OUTPUT.relative_to(ROOT)} with {len(rows)} template rows.")
    print(f"Declared data types ({len(DECLARATIONS)}): {selected}")
    print("Global answers: data collected/shared=YES, encrypted in transit=YES, deletion-request mechanism=NO")

    if args.submit:
        token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "").strip()
        if not token:
            raise SystemExit("GOOGLE_PLAY_ACCESS_TOKEN is required with --submit")
        submit(csv_text, token)
    else:
        print("Dry run only; pass --submit with GOOGLE_PLAY_ACCESS_TOKEN to publish.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
