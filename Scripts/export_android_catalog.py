#!/usr/bin/env python3
"""Build the Android question asset from the CI-verified review catalog.

QuestionReview.csv is regenerated from EZTriviaCore and diffed in the existing
Swift CI job. This exporter converts that already-verified data into a compact,
platform-neutral JSON payload while reconstructing QuestionBank.all ordering so
seeded Android modes can consume the same bank order as iOS.

Flag questions carry one extra piece of source metadata: the flag code and the
small set of visually-confusable codes that iOS refuses to offer together. That
lets Android reproduce Friend Challenge v3's seeded flag distractor redraw
exactly instead of freezing the four review-export choices.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path

CATEGORY_ORDER = [
    "football",
    "basketball",
    "soccer",
    "history",
    "science",
    "movies",
    "tv",
    "geography",
    "music",
    "animals",
    "food",
    "literature",
    "art",
    "mythology",
    "videoGames",
]

CATEGORY_TITLES = {
    "Football": "football",
    "Basketball": "basketball",
    "Soccer": "soccer",
    "World Flags": "flags",
    "History": "history",
    "Science": "science",
    "Movies": "movies",
    "TV": "tv",
    "Geography": "geography",
    "Music": "music",
    "Animals": "animals",
    "Food & Drink": "food",
    "Books & Literature": "literature",
    "Art & Architecture": "art",
    "Mythology & Legends": "mythology",
    "Video Games": "videoGames",
}

DIFFICULTY_ORDER = {"easy": 0, "medium": 1, "hard": 2}
EXPECTED_QUESTION_COUNT = 2341
EXPECTED_FLAG_COUNT = 249
EXPECTED_ASKABLE_FLAG_COUNT = 241

FLAG_ENTRY_RE = re.compile(
    r'FlagEntry\("(?P<code>[A-Z]{2})",\s*"(?P<name>(?:[^"\\]|\\.)*)",\s*'
    r'\.(?P<difficulty>easy|medium|hard)(?P<tail>.*?)'
    r'(?=\n\s*FlagEntry\(|\n\s*\])',
    re.DOTALL,
)
CONFUSABLE_RE = re.compile(r"confusable:\s*\[(?P<codes>[^\]]*)\]")
QUOTED_CODE_RE = re.compile(r'"([A-Z]{2})"')


def parse_sequence(question_id: str) -> int:
    try:
        return int(question_id.rsplit("-", 1)[1])
    except ValueError:
        return 0


def parse_flag_catalog(path: Path) -> dict[str, dict[str, object]]:
    """Read the compact gameplay metadata from Swift's FlagCatalog source.

    FlagCatalog remains the source of truth. Parsing it here avoids maintaining
    a second Android-only confusable-pair list that could silently drift when a
    flag is reclassified on iOS.
    """

    source = path.read_text(encoding="utf-8")
    entries: dict[str, dict[str, object]] = {}

    for match in FLAG_ENTRY_RE.finditer(source):
        code = match.group("code")
        tail = match.group("tail")
        confusable_match = CONFUSABLE_RE.search(tail)
        confusable = (
            QUOTED_CODE_RE.findall(confusable_match.group("codes"))
            if confusable_match
            else []
        )
        entries[code] = {
            "difficulty": match.group("difficulty"),
            "askable": not bool(re.search(r"\baskable:\s*false\b", tail)),
            "confusable": confusable,
        }

    if len(entries) != EXPECTED_FLAG_COUNT:
        raise SystemExit(
            f"expected {EXPECTED_FLAG_COUNT} FlagEntry declarations, found {len(entries)}"
        )
    askable_count = sum(1 for entry in entries.values() if entry["askable"])
    if askable_count != EXPECTED_ASKABLE_FLAG_COUNT:
        raise SystemExit(
            f"expected {EXPECTED_ASKABLE_FLAG_COUNT} askable flags, found {askable_count}"
        )
    return entries


def bank_sort_key(question: dict[str, object]) -> tuple[object, ...]:
    category = str(question["category"])
    difficulty = str(question["difficulty"])

    # QuestionBank builds all text categories first, in a fixed category order,
    # then appends flags. Text seeds are stored Easy/Medium/Hard in authored
    # sequence. FlagCatalog is ordered by difficulty then display name.
    if category == "flags":
        answers = question["answers"]
        correct_index = int(question["correctAnswerIndex"])
        assert isinstance(answers, list)
        correct_answer = str(answers[correct_index])
        return (1, DIFFICULTY_ORDER[difficulty], correct_answer, str(question["id"]))

    return (
        0,
        CATEGORY_ORDER.index(category),
        DIFFICULTY_ORDER[difficulty],
        parse_sequence(str(question["id"])),
        str(question["id"]),
    )


def convert(input_path: Path, flag_catalog_path: Path) -> dict[str, object]:
    questions: list[dict[str, object]] = []
    seen_ids: set[str] = set()
    flag_metadata = parse_flag_catalog(flag_catalog_path)

    with input_path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            question_id = row["id"]
            if question_id in seen_ids:
                raise SystemExit(f"duplicate question id: {question_id}")
            seen_ids.add(question_id)

            try:
                category = CATEGORY_TITLES[row["category"]]
            except KeyError as exc:
                raise SystemExit(f"unknown category title: {row['category']}") from exc

            difficulty = row["difficulty"].lower()
            if difficulty not in DIFFICULTY_ORDER:
                raise SystemExit(f"unknown difficulty: {row['difficulty']}")

            answers = [row["answer_a"], row["answer_b"], row["answer_c"], row["answer_d"]]
            if len(set(answers)) != 4:
                raise SystemExit(f"question {question_id} does not have four distinct answers")

            try:
                correct_index = answers.index(row["correct_answer"])
            except ValueError as exc:
                raise SystemExit(f"question {question_id} correct answer is not in its answer list") from exc

            question: dict[str, object] = {
                "id": question_id,
                "category": category,
                "difficulty": difficulty,
                "prompt": row["prompt"],
                "visual": row["visual"] or None,
                "answers": answers,
                "correctAnswerIndex": correct_index,
                "explanation": row["explanation"],
            }

            if category == "flags":
                visual = row["visual"]
                if not visual.startswith("flag-"):
                    raise SystemExit(f"flag question {question_id} has invalid visual {visual!r}")
                flag_code = visual.removeprefix("flag-").upper()
                metadata = flag_metadata.get(flag_code)
                if metadata is None:
                    raise SystemExit(f"flag question {question_id} has unknown code {flag_code}")
                if not metadata["askable"]:
                    raise SystemExit(f"unaskable flag {flag_code} appears in QuestionReview.csv")
                if metadata["difficulty"] != difficulty:
                    raise SystemExit(
                        f"flag {flag_code} difficulty mismatch: review={difficulty} "
                        f"catalog={metadata['difficulty']}"
                    )
                question["flagCode"] = flag_code
                question["confusableFlagCodes"] = metadata["confusable"]

            questions.append(question)

    if len(questions) != EXPECTED_QUESTION_COUNT:
        raise SystemExit(
            f"expected {EXPECTED_QUESTION_COUNT} questions, found {len(questions)}"
        )

    flag_questions = [question for question in questions if question["category"] == "flags"]
    if len(flag_questions) != EXPECTED_ASKABLE_FLAG_COUNT:
        raise SystemExit(
            f"expected {EXPECTED_ASKABLE_FLAG_COUNT} flag questions, found {len(flag_questions)}"
        )

    questions.sort(key=bank_sort_key)
    return {"schemaVersion": 1, "questions": questions}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("QuestionReview.csv"))
    parser.add_argument(
        "--flag-catalog",
        type=Path,
        default=Path("Sources/EZTriviaCore/FlagCatalog.swift"),
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    payload = convert(args.input, args.flag_catalog)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
