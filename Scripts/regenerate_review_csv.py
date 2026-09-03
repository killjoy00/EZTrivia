#!/usr/bin/env python3
"""Regenerate QuestionReview.csv without a Swift toolchain.

`swift run QuestionCatalogExporter` is the source of truth and CI diffs against
it. This is a stand-in for environments with no Swift available: it parses the
authored seed files directly and reproduces the exporter's output.

Correctness is not assumed. `--verify` regenerates from the current sources and
diffs against the committed CSV; if the two disagree, this parser is wrong and
its output must not be committed. Run --verify before trusting any regeneration,
and again after, so a content change is the only difference in the diff.

Flag rows are carried over verbatim from the committed CSV rather than rebuilt.
They come from a seeded generator whose exact permutations are a Swift
implementation detail, and nothing here ever edits them.

    python3 Scripts/regenerate_review_csv.py --verify   # prove the parser matches
    python3 Scripts/regenerate_review_csv.py            # rewrite the CSV
"""

from __future__ import annotations

import argparse
import ast
import csv
import io
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "QuestionReview.csv"
QUESTIONS_DIR = ROOT / "Sources" / "EZTriviaCore" / "Questions"
BANK_PATH = ROOT / "Sources" / "EZTriviaCore" / "QuestionBank.swift"
MODELS_PATH = ROOT / "Sources" / "EZTriviaCore" / "Models.swift"
METADATA_PATH = ROOT / "Sources" / "EZTriviaCore" / "QuestionReviewMetadata.swift"

FLAG_CATEGORY_TITLE = "World Flags"
DIFFICULTY_ORDER = {"Easy": 0, "Medium": 1, "Hard": 2}
HEADER = [
    "id", "category", "difficulty", "prompt", "visual", "answer_a", "answer_b",
    "answer_c", "answer_d", "correct_answer", "explanation", "review_note",
    "source_url", "verified_on", "review_after",
]


def swift_string_literals(text: str) -> list[str]:
    """Every double-quoted Swift literal in `text`, in order, unescaped.

    Swift and Python agree on the escapes this bank actually uses (\\" and \\\\),
    so the literal is decoded by reusing Python's own string parser rather than
    hand-rolling one.
    """
    literals = []
    index = 0
    length = len(text)
    while index < length:
        if text[index] != '"':
            index += 1
            continue
        start = index
        index += 1
        while index < length:
            if text[index] == "\\":
                index += 2
                continue
            if text[index] == '"':
                break
            index += 1
        raw = text[start : index + 1]
        literals.append(ast.literal_eval(raw))
        index += 1
    return literals


def parse_category_order() -> list[tuple[str, str]]:
    """(rawValue, title) for every category, in `TriviaCategory.allCases` order."""
    models = MODELS_PATH.read_text(encoding="utf-8")
    cases = re.search(r"case\s+((?:\w+\s*,\s*)+\w+)\s*\n", models)
    if not cases:
        sys.exit("could not read TriviaCategory cases from Models.swift")
    raw_values = [value.strip() for value in cases.group(1).split(",")]

    titles = {}
    title_block = re.search(
        r"public var title: String \{\s*switch self \{(.*?)\}\s*\}", models, re.S
    )
    if not title_block:
        sys.exit("could not read TriviaCategory titles from Models.swift")
    for raw, title in re.findall(r"case \.(\w+):\s*\"((?:[^\"\\]|\\.)*)\"", title_block.group(1)):
        titles[raw] = ast.literal_eval(f'"{title}"')

    missing = [raw for raw in raw_values if raw not in titles]
    if missing:
        sys.exit(f"categories missing a title: {missing}")
    return [(raw, titles[raw]) for raw in raw_values]


def parse_seed_wiring() -> dict[str, str]:
    """category rawValue -> Swift enum providing its seeds, from QuestionBank."""
    bank = BANK_PATH.read_text(encoding="utf-8")
    block = re.search(r"seedsByCategory[^=]*=\s*\[(.*?)\n    \]", bank, re.S)
    if not block:
        sys.exit("could not read seedsByCategory from QuestionBank.swift")
    return dict(re.findall(r"\(\.(\w+),\s*(\w+)\.seeds\)", block.group(1)))


def parse_seeds(enum_name: str) -> dict[str, list[dict]]:
    """Parse one Questions/*.swift file into {difficulty: [seed, ...]}."""
    path = QUESTIONS_DIR / f"{enum_name.replace('Questions', '')}Questions.swift"
    if not path.exists():
        candidates = [p for p in QUESTIONS_DIR.glob("*.swift") if enum_name in p.read_text(encoding="utf-8")]
        if len(candidates) != 1:
            sys.exit(f"cannot locate source file for {enum_name}")
        path = candidates[0]
    text = path.read_text(encoding="utf-8")

    tiers: dict[str, list[dict]] = {}
    for tier in ("easy", "medium", "hard"):
        block = re.search(
            rf"static let {tier}: \[QuestionSeed\] = \[(.*?)\n    \]", text, re.S
        )
        if not block:
            sys.exit(f"{path.name} is missing its `{tier}` tier")
        tiers[tier.capitalize()] = parse_seed_block(block.group(1), path.name, tier)
    return tiers


def parse_seed_block(block: str, filename: str, tier: str) -> list[dict]:
    seeds = []
    for match in re.finditer(r"QuestionSeed\(", block):
        start = match.end()
        depth = 1
        index = start
        in_string = False
        while index < len(block) and depth:
            char = block[index]
            if in_string:
                if char == "\\":
                    index += 2
                    continue
                if char == '"':
                    in_string = False
            elif char == '"':
                in_string = True
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if not depth:
                    break
            index += 1
        body = block[start:index]

        literals = swift_string_literals(body)
        if len(literals) < 6:
            sys.exit(f"{filename} {tier}: seed has {len(literals)} literals, expected >= 6")
        prompt, *rest = literals
        answers, explanation = rest[:4], rest[4]

        # The keyed index is the bare integer between the answer array and the
        # explanation, so it is read from the text between those two literals.
        after_answers = body.split("]", 1)
        if len(after_answers) < 2:
            sys.exit(f"{filename} {tier}: could not find the answer array for '{prompt[:40]}'")
        index_match = re.search(r"\]\s*,\s*(\d+)", body)
        if not index_match:
            sys.exit(f"{filename} {tier}: could not find keyed index for '{prompt[:40]}'")

        seeds.append(
            {
                "prompt": prompt,
                "answers": answers,
                "correct": int(index_match.group(1)),
                "explanation": explanation,
                "visual": "",
            }
        )
    return seeds


def parse_metadata() -> dict[str, tuple[str, str, str]]:
    """question id -> (source_url, verified_on, review_after)."""
    if not METADATA_PATH.exists():
        return {}
    text = METADATA_PATH.read_text(encoding="utf-8")

    constants = dict(
        (name, ast.literal_eval(f'"{value}"'))
        for name, value in re.findall(
            r"private static let (\w+)\s*=\s*\"((?:[^\"\\]|\\.)*)\"", text
        )
    )

    registry = re.search(r"byQuestionID: \[String: QuestionReviewMetadata\] = \[(.*?)\n    \]", text, re.S)
    if not registry:
        return {}

    metadata = {}
    pattern = re.compile(
        r"\"([^\"]+)\":\s*\.init\(\s*sourceURL:\s*(\w+|\"(?:[^\"\\]|\\.)*\")\s*,"
        r"\s*verifiedOn:\s*\"([^\"]*)\"\s*,\s*reviewAfter:\s*\"([^\"]*)\"\s*\)",
        re.S,
    )
    for question_id, source, verified, review in pattern.findall(registry.group(1)):
        url = constants.get(source) if not source.startswith('"') else ast.literal_eval(source)
        if url is None:
            sys.exit(f"unresolved source constant {source} for {question_id}")
        metadata[question_id] = (url, verified, review)
    return metadata


def sequence_of(question_id: str) -> int:
    tail = question_id.rsplit("-", 1)[-1]
    try:
        return int(tail)
    except ValueError:
        return 0


def build_text_rows() -> list[list[str]]:
    categories = parse_category_order()
    wiring = parse_seed_wiring()
    metadata = parse_metadata()

    rows = []
    for raw_value, title in categories:
        enum_name = wiring.get(raw_value)
        if enum_name is None:
            continue  # flags carry no authored seed file
        tiers = parse_seeds(enum_name)
        for tier_title in ("Easy", "Medium", "Hard"):
            for offset, seed in enumerate(tiers[tier_title], start=1):
                question_id = f"{raw_value}-{tier_title.lower()}-{offset}"
                source, verified, review = metadata.get(question_id, ("", "", ""))
                rows.append(
                    [
                        question_id,
                        title,
                        tier_title,
                        seed["prompt"],
                        seed["visual"],
                        *seed["answers"],
                        seed["answers"][seed["correct"]],
                        seed["explanation"],
                        "",
                        source,
                        verified,
                        review,
                    ]
                )
    return rows


def existing_flag_rows() -> list[list[str]]:
    with CSV_PATH.open(newline="", encoding="utf-8") as handle:
        reader = csv.reader(handle)
        next(reader)
        return [row for row in reader if row[1] == FLAG_CATEGORY_TITLE]


def render(rows: list[list[str]]) -> str:
    ordered = sorted(
        rows,
        key=lambda row: (row[1], DIFFICULTY_ORDER.get(row[2], 0), sequence_of(row[0]), row[0]),
    )
    buffer = io.StringIO()
    for row in [HEADER, *ordered]:
        buffer.write(",".join('"' + field.replace('"', '""') + '"' for field in row))
        buffer.write("\n")
    return buffer.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="diff against the committed CSV instead of writing")
    args = parser.parse_args()

    generated = render(build_text_rows() + existing_flag_rows())

    if args.verify:
        current = CSV_PATH.read_text(encoding="utf-8")
        if generated == current:
            print(f"OK: parser reproduces {CSV_PATH.name} byte-for-byte")
            return 0
        current_lines = current.splitlines()
        generated_lines = generated.splitlines()
        print(f"MISMATCH: committed {len(current_lines)} lines, generated {len(generated_lines)}")
        shown = 0
        for number, (left, right) in enumerate(zip(current_lines, generated_lines), start=1):
            if left != right and shown < 5:
                print(f"  line {number}\n    committed: {left[:150]}\n    generated: {right[:150]}")
                shown += 1
        return 1

    CSV_PATH.write_text(generated, encoding="utf-8")
    print(f"wrote {CSV_PATH.name} ({len(generated.splitlines()) - 1} questions)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
