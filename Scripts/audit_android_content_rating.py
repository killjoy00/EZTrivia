#!/usr/bin/env python3
"""Produce deterministic, non-secret evidence for the Google Play IARC questionnaire.

This scanner is deliberately evidence-only. Keyword matches identify catalog rows
that deserve review; they do not themselves decide an IARC answer. That matters
for terms such as "Casino" that can appear as a movie title without representing
gambling functionality.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path
from typing import Iterable

TEXT_FIELDS = (
    "prompt",
    "answer_a",
    "answer_b",
    "answer_c",
    "answer_d",
    "correct_answer",
    "explanation",
)

PATTERNS: dict[str, tuple[str, ...]] = {
    "referredViolence": (
        r"\bassassinat(?:e|ed|es|ing|ion)\w*\b",
        r"\bmurder\w*\b",
        r"\bkilled\b",
        r"\bslain\b",
        r"\bwar\b",
        r"\bwars\b",
        r"\bbattle\b",
        r"\bbomb(?:ing|ings|ed|s)?\b",
        r"\bexecution\w*\b",
    ),
    "illegalOrRecreationalDrugs": (
        r"\bdrug trade\b",
        r"\billegal drugs?\b",
        r"\brecreational drugs?\b",
        r"\bmarijuana\b",
        r"\bcocaine\b",
        r"\bheroin\b",
        r"\blsd\b",
        r"\bmeth(?:amphetamine)?\b",
    ),
    "alcohol": (
        r"\balcohol\b",
        r"\bbeer\b",
        r"\bwine\b",
        r"\bwines\b",
        r"\bchampagne\b",
        r"\brum\b",
        r"\bwhisk(?:e)?y\b",
        r"\bvodka\b",
        r"\bgin\b",
        r"\bprohibition\b",
    ),
    "sexOrNudity": (
        r"\bsex\b",
        r"\bsexual\w*\b",
        r"\bnude\b",
        r"\bnudity\b",
        r"\bnaked\b",
        r"\berotic\w*\b",
    ),
    "gamblingTerms": (
        r"\bgambl\w*\b",
        r"\bcasino\b",
        r"\bpoker\b",
        r"\bblackjack\b",
        r"\broulette\b",
        r"\bbetting\b",
        r"\bwager\w*\b",
    ),
    "strongProfanity": (
        r"\bfuck\w*\b",
        r"\bshit\w*\b",
        r"\bbitch\w*\b",
        r"\bcunt\w*\b",
        r"\bmotherfuck\w*\b",
    ),
}


def combined_text(row: dict[str, str]) -> str:
    return " ".join(row.get(field, "") or "" for field in TEXT_FIELDS)


def matching_terms(text: str, patterns: Iterable[str]) -> list[str]:
    found: list[str] = []
    for pattern in patterns:
        if re.search(pattern, text, flags=re.IGNORECASE):
            found.append(pattern)
    return found


def audit(csv_path: Path) -> dict[str, object]:
    with csv_path.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))

    evidence: dict[str, list[dict[str, object]]] = {name: [] for name in PATTERNS}
    for row in rows:
        text = combined_text(row)
        for name, patterns in PATTERNS.items():
            matched = matching_terms(text, patterns)
            if matched:
                evidence[name].append(
                    {
                        "id": row["id"],
                        "category": row["category"],
                        "difficulty": row["difficulty"],
                        "prompt": row["prompt"],
                        "explanation": row["explanation"],
                        "matchedPatterns": matched,
                    }
                )

    return {
        "source": str(csv_path),
        "questionCount": len(rows),
        "candidateCounts": {name: len(items) for name, items in evidence.items()},
        "candidates": evidence,
        "notes": [
            "Keyword candidates are evidence for manual review, not automatic IARC answers.",
            "The scanner intentionally includes possible false positives such as titles containing Casino.",
            "App features such as digital purchases, UGC, multiplayer, and gambling mechanics must be audited from code/product behavior separately.",
        ],
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="QuestionReview.csv")
    parser.add_argument("--output", default="content-rating-evidence.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source = Path(args.input)
    output = Path(args.output)
    result = audit(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(f"Audited {result['questionCount']} questions from {source}")
    for name, count in result["candidateCounts"].items():
        print(f"{name}: {count}")
    print(f"Wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
