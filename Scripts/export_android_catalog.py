#!/usr/bin/env python3
"""Build the Android question asset from the CI-verified review catalog.

QuestionReview.csv is regenerated from EZTriviaCore and diffed in the existing
Swift CI job. This exporter converts that already-verified data into a compact,
platform-neutral JSON payload while reconstructing QuestionBank.all ordering so
seeded Android modes can consume the same bank order as iOS.
"""

from __future__ import annotations

import argparse
import csv
import json
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


def parse_sequence(question_id: str) -> int:
    try:
        return int(question_id.rsplit("-", 1)[1])
    except ValueError:
        return 0


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


def convert(input_path: Path) -> dict[str, object]:
    questions: list[dict[str, object]] = []
    seen_ids: set[str] = set()

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

            questions.append(
                {
                    "id": question_id,
                    "category": category,
                    "difficulty": difficulty,
                    "prompt": row["prompt"],
                    "visual": row["visual"] or None,
                    "answers": answers,
                    "correctAnswerIndex": correct_index,
                    "explanation": row["explanation"],
                }
            )

    if len(questions) != EXPECTED_QUESTION_COUNT:
        raise SystemExit(
            f"expected {EXPECTED_QUESTION_COUNT} questions, found {len(questions)}"
        )

    questions.sort(key=bank_sort_key)
    return {"schemaVersion": 1, "questions": questions}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("QuestionReview.csv"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    payload = convert(args.input)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
