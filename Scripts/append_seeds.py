#!/usr/bin/env python3
"""Append or replace authored QuestionSeed entries in a category tier.

Editing the seed files by hand at volume invites a misplaced bracket that the
Swift compiler would catch only in CI, minutes later. This inserts before the
closing bracket of the named tier array and refuses anything it cannot place
unambiguously.

It also refuses a prompt that repeats one already in the category. That check
exists because it was learned the hard way: a 200-question expansion authored
without it reintroduced 37 prompts the bank already had, several of them word
for word, and only the editorial report caught them.

Input is JSON on stdin. To append:

    {"file": "FoodQuestions.swift", "tier": "easy", "seeds": [
        {"prompt": "...", "answers": ["a","b","c","d"], "correct": 2,
         "explanation": "..."}
    ]}

To replace an existing seed in place, key it on its 1-based position within the
tier — the trailing number of its question id, so `food-easy-47` is index 47:

    {"file": "FoodQuestions.swift", "tier": "easy", "replace": [
        {"index": 47, "seed": {...}}
    ]}
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

QUESTIONS = Path(__file__).resolve().parent.parent / "Sources" / "EZTriviaCore" / "Questions"

# Mirrors `normalizedTokens` in the Swift suite and `tokens` in the analyzer, so
# the near-duplicate judgement here is the one CI and the report will make.
STOP_WORDS = {
    "a", "an", "and", "are", "for", "from", "in", "is", "it", "of",
    "on", "or", "that", "the", "this", "to", "was", "were", "with",
}

DUPLICATE_OVERLAP = 0.80


def tokens(value: str) -> set[str]:
    return {
        word
        for word in re.split(r"[^0-9A-Za-z]+", value.lower())
        if word and word not in STOP_WORDS
    }


def swift_literal(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def render(seed: dict) -> str:
    answers = ", ".join(swift_literal(a) for a in seed["answers"])
    return (
        f'        QuestionSeed({swift_literal(seed["prompt"])},\n'
        f'                     [{answers}], {seed["correct"]},\n'
        f'                     {swift_literal(seed["explanation"])}),\n'
    )


def unescape(raw: str) -> str:
    return raw.replace('\\"', '"').replace("\\\\", "\\")


def existing_prompts(text: str) -> list[str]:
    """Every prompt already in the file, across all three tiers."""
    return [
        unescape(raw)
        for raw in re.findall(r'QuestionSeed\("((?:[^"\\]|\\.)*)"', text)
    ]


def existing_answers(text: str) -> set[str]:
    """Every keyed answer already in the file, normalized to a token string.

    A new question that keys an answer the category already keys is nearly
    always the same fact reworded — "Which lake holds the most fresh water?"
    against "Which lake holds the largest volume of fresh water?". Forbidding
    the reuse outright is blunt, but for authoring *new* content that bluntness
    is the point: it forces genuinely new ground.
    """
    keyed = set()
    pattern = re.compile(
        r'QuestionSeed\("(?:[^"\\]|\\.)*",\s*\[(.*?)\],\s*(\d+)', re.S
    )
    for answers_blob, index in pattern.findall(text):
        answers = [unescape(a) for a in re.findall(r'"((?:[^"\\]|\\.)*)"', answers_blob)]
        if len(answers) == 4 and 0 <= int(index) < 4:
            keyed.add(" ".join(sorted(tokens(answers[int(index)]))))
    return keyed


def validate(
    seed: dict, where: str, prior: list[str], keyed: set[str] | None = None
) -> list[str]:
    """The checks the Swift suite enforces, applied before anything is written."""
    problems = []
    prompt, answers = seed["prompt"], seed["answers"]
    explanation = seed["explanation"]

    if not prompt.endswith("?"):
        problems.append(f"{where}: prompt is not a question")
    if len(prompt.split()) > 20:
        problems.append(f"{where}: prompt is {len(prompt.split())} words, limit 20")
    if len(answers) != 4 or len(set(answers)) != 4:
        problems.append(f"{where}: needs four distinct answers")
    if not 0 <= seed["correct"] < 4:
        problems.append(f"{where}: correct index out of range")
    if any(len(a.split()) > 12 for a in answers):
        problems.append(f"{where}: an answer runs over 12 words")
    if not 8 <= len(explanation.split()) <= 24:
        problems.append(f"{where}: explanation is {len(explanation.split())} words, want 8-24")

    # The correct answer must never be the single longest option: "pick the
    # longest" is the heuristic the whole bank is defended against, and new
    # content is the cheapest place to hold the line.
    lengths = [len(a) for a in answers]
    if lengths[seed["correct"]] == max(lengths) and lengths.count(max(lengths)) == 1:
        problems.append(f"{where}: correct answer is the sole longest option")

    # An explanation that shares no vocabulary with its answer usually means it
    # is describing a distractor instead.
    if tokens(answers[seed["correct"]]).isdisjoint(tokens(explanation)):
        problems.append(f"{where}: explanation never names the correct answer")

    if keyed is not None:
        answer_key = " ".join(sorted(tokens(answers[seed["correct"]])))
        if answer_key in keyed:
            problems.append(
                f"{where}: '{answers[seed['correct']]}' is already the answer to "
                f"another question in this category"
            )

    mine = tokens(prompt)
    if mine:
        for other in prior:
            other_tokens = tokens(other)
            if not other_tokens:
                continue
            overlap = len(mine & other_tokens) / len(mine | other_tokens)
            if overlap >= DUPLICATE_OVERLAP:
                problems.append(
                    f"{where}: {overlap:.0%} token overlap with existing prompt '{other}'"
                )
                break

    return problems


def tier_bounds(text: str, tier: str, path: Path) -> re.Match:
    match = re.compile(
        rf"(static let {tier}: \[QuestionSeed\] = \[.*?)(\n    \])", re.S
    ).search(text)
    if not match:
        sys.exit(f"could not locate the `{tier}` array in {path.name}")
    return match


def tier_seed_spans(text: str, tier: str, path: Path) -> list[tuple[int, int]]:
    """Character range of every QuestionSeed in `tier`, in authored order.

    Positions are the identity here rather than prompt text, because the very
    problem being repaired is prompts that appear twice.
    """
    match = tier_bounds(text, tier, path)
    body_start, body_end = match.start(1), match.end(1)
    spans = []
    for hit in re.finditer(r"QuestionSeed\(", text[body_start:body_end]):
        start = body_start + hit.start()
        line_start = text.rfind("\n", 0, start) + 1
        end = text.find("),\n", start)
        if end < 0 or end > body_end:
            sys.exit(f"{path.name}: could not find the end of a seed in {tier}")
        spans.append((line_start, end + len("),\n")))
    return spans


def main() -> int:
    request = json.load(sys.stdin)
    path = QUESTIONS / request["file"]
    tier = request["tier"]

    if not path.exists():
        sys.exit(f"no such file: {path}")
    text = path.read_text(encoding="utf-8")

    if "replace" in request:
        edits = request["replace"]
        spans = tier_seed_spans(text, tier, path)
        for edit in edits:
            if not 1 <= edit["index"] <= len(spans):
                sys.exit(f"{path.name}: {tier} has {len(spans)} seeds, no index {edit['index']}")

        # The seeds being retired are not competition for the ones replacing
        # them, so they are cut from the text the comparison set is read from.
        # Cutting spans rather than filtering by prompt keeps the *other* copy
        # of a duplicated prompt in the set, which is the whole point here.
        remainder = text
        for edit in sorted(edits, key=lambda e: e["index"], reverse=True):
            start, end = spans[edit["index"] - 1]
            remainder = remainder[:start] + remainder[end:]
        prior = existing_prompts(remainder)
        keyed = existing_answers(remainder)

        problems = []
        for edit in edits:
            problems += validate(
                edit["seed"],
                f"{request['file']} {tier} #{edit['index']}",
                prior,
                keyed,
            )
            prior.append(edit["seed"]["prompt"])
            keyed.add(" ".join(sorted(tokens(edit["seed"]["answers"][edit["seed"]["correct"]]))))
        if problems:
            print("\n".join(problems))
            return 1

        # Apply back to front so earlier spans stay valid.
        for edit in sorted(edits, key=lambda e: e["index"], reverse=True):
            start, end = spans[edit["index"] - 1]
            text = text[:start] + render(edit["seed"]) + text[end:]
        path.write_text(text, encoding="utf-8")
        print(f"{path.name}: replaced {len(edits)} in {tier}")
        return 0

    seeds = request["seeds"]
    prior = existing_prompts(text)
    keyed = existing_answers(text)
    problems = []
    for index, seed in enumerate(seeds):
        problems += validate(seed, f"{request['file']} {tier} #{index + 1}", prior, keyed)
        prior.append(seed["prompt"])
        keyed.add(" ".join(sorted(tokens(seed["answers"][seed["correct"]]))))
    if problems:
        print("\n".join(problems))
        return 1

    match = tier_bounds(text, tier, path)
    addition = "".join(render(seed) for seed in seeds)

    # The last seed in a tier may or may not carry a trailing comma; both are
    # valid Swift while it is last, and only one stays valid once something
    # follows it. Splicing without checking produced four files that parsed
    # here and failed to compile in CI.
    head = text[: match.end(1)].rstrip()
    if not head.endswith(","):
        head += ","

    updated = head + "\n" + addition.rstrip("\n") + match.group(2) + text[match.end(2):]
    path.write_text(updated, encoding="utf-8")
    print(f"{path.name}: +{len(seeds)} to {tier}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
