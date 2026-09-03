#!/usr/bin/env python3
"""Verify the seed files are well-formed Swift array literals.

There is no Swift toolchain in every environment that edits this bank, so a
missing separator can reach CI and fail the build minutes later. That is not
hypothetical: appending after a final seed that carried no trailing comma
spliced two expressions together in four places, and a paren-balance check
was too weak to see it — the parens balanced perfectly.

This parses each tier array the way Swift does: a sequence of QuestionSeed(...)
elements separated by commas, and nothing else between them.

    python3 Scripts/check_seed_syntax.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

QUESTIONS = Path(__file__).resolve().parent.parent / "Sources" / "EZTriviaCore" / "Questions"
EXPECTED = {"easy": 50, "medium": 50, "hard": 40}


def seed_end(text: str, start: int) -> int:
    """Index just past the closing paren of the QuestionSeed( at `start`."""
    depth = 0
    index = start
    in_string = False
    while index < len(text):
        char = text[index]
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
            if depth == 0:
                return index + 1
        index += 1
    return -1


def check(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    problems = []

    for tier, expected in EXPECTED.items():
        block = re.search(
            rf"static let {tier}: \[QuestionSeed\] = \[(.*?)\n    \]", text, re.S
        )
        if not block:
            problems.append(f"{path.name}: missing the `{tier}` array")
            continue

        body = block.group(1)
        cursor = 0
        count = 0
        while True:
            hit = body.find("QuestionSeed(", cursor)
            if hit < 0:
                break
            # Everything between the previous element and this one must be a
            # separator: whitespace, and exactly one comma once past the first.
            gap = body[cursor:hit]
            commas = gap.count(",")
            if count and commas != 1:
                line = text[: block.start(1) + hit].count("\n") + 1
                problems.append(
                    f"{path.name}:{line} {tier} element {count + 1} is preceded by "
                    f"{commas} separator commas, expected 1"
                )
            if gap.strip(" \t\n,"):
                line = text[: block.start(1) + hit].count("\n") + 1
                problems.append(f"{path.name}:{line} unexpected text before {tier} element {count + 1}")

            end = seed_end(body, hit)
            if end < 0:
                problems.append(f"{path.name}: unterminated QuestionSeed in {tier}")
                break
            cursor = end
            count += 1

        trailer = body[cursor:]
        if trailer.strip(" \t\n,"):
            problems.append(f"{path.name}: unexpected text after the last {tier} element")
        if trailer.count(",") > 1:
            problems.append(f"{path.name}: {tier} has a doubled trailing comma")
        if count != expected:
            problems.append(f"{path.name}: {tier} has {count} seeds, expected {expected}")

    return problems


def main() -> int:
    problems = []
    files = sorted(QUESTIONS.glob("*Questions.swift"))
    for path in files:
        problems += check(path)

    if problems:
        print("\n".join(problems))
        print(f"\n{len(problems)} problems across {len(files)} files")
        return 1
    print(f"OK: {len(files)} seed files are well-formed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
