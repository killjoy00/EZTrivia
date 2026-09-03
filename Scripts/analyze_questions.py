#!/usr/bin/env python3
"""Editorial health report for the question bank.

The app collects no gameplay telemetry -- nothing about which questions players
miss ever leaves the device -- so the bank cannot be judged by player results.
What can be judged is whether a question is *guessable without knowing the
answer*, and whether a pool has quietly drifted into repetition. That is what
this reports on.

The Swift test suite already fails the build on the hard limits. This tool is
the other half: it ranks every pool so a marginal one is visible long before it
trips a gate, and it surfaces signals the tests do not encode at all (near
duplicate prompts, answer-position drift, copy that runs long).

    python3 Scripts/analyze_questions.py                  # full report
    python3 Scripts/analyze_questions.py --top 5          # worst 5 per section
    python3 Scripts/analyze_questions.py --fail-on-warn   # non-zero exit on warnings
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

CSV_PATH = Path(__file__).resolve().parent.parent / "QuestionReview.csv"
FLAG_CATEGORY = "World Flags"
ANSWER_COLUMNS = ("answer_a", "answer_b", "answer_c", "answer_d")

# Mirrors QuestionQualityTests.swift so the report and the build agree on what
# "too biased" means. Changing a threshold here without changing it there makes
# this tool lie about whether CI would pass.
POOL_LONGEST_LIMIT = 0.35
CONSPICUOUS_POOL_LIMIT = 10
CONSPICUOUS_RATE_LIMIT = 0.09
KEYED_ANSWER_REPEAT_LIMIT = 3
REINFORCEMENT_FLOOR = 0.60

STOP_WORDS = {
    "a", "an", "and", "are", "for", "from", "in", "is", "it", "of",
    "on", "or", "that", "the", "this", "to", "was", "were", "with",
}


def tokens(value: str) -> set[str]:
    """Token set matching the Swift `normalizedTokens` helper."""
    return {
        word
        for word in re.split(r"[^0-9A-Za-z]+", value.lower())
        if word and word not in STOP_WORDS
    }


def load_rows() -> list[dict[str, str]]:
    if not CSV_PATH.exists():
        sys.exit(f"missing {CSV_PATH}; run `swift run QuestionCatalogExporter > QuestionReview.csv`")
    with CSV_PATH.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def answers_of(row: dict[str, str]) -> list[str]:
    return [row[column] for column in ANSWER_COLUMNS]


def correct_index(row: dict[str, str]) -> int | None:
    """The CSV stores the correct answer as text, not an index."""
    try:
        return answers_of(row).index(row["correct_answer"])
    except ValueError:
        return None


def longest_answer_credit(row: dict[str, str]) -> float:
    """How often "pick the longest" wins on this question, as an expected value.

    Deliberately identical to `theLongestAnswerIsNotUsuallyTheCorrectOneInAnyPool`
    in the Swift suite, including the fractional credit for ties: answers are
    shuffled before presentation, so a guesser facing two equally long options
    picks the right one half the time. A stricter sole-longest rule would report
    a lower number than the build actually enforces, and this tool exists to
    predict the build.
    """
    index = correct_index(row)
    if index is None:
        return 0.0
    lengths = [len(answer) for answer in answers_of(row)]
    longest = max(lengths)
    if lengths[index] != longest:
        return 0.0
    return 1.0 / lengths.count(longest)


def is_sole_longest(row: dict[str, str]) -> bool:
    """True when the correct answer is strictly longer than every distractor."""
    index = correct_index(row)
    if index is None:
        return False
    lengths = [len(answer) for answer in answers_of(row)]
    longest = max(lengths)
    return lengths[index] == longest and lengths.count(longest) == 1


def is_conspicuous(row: dict[str, str]) -> bool:
    """Sole-longest *and* long enough that the gap is visible at a glance."""
    if not is_sole_longest(row):
        return False
    lengths = sorted((len(answer) for answer in answers_of(row)), reverse=True)
    runner_up = max(lengths[1], 1)
    return lengths[0] - runner_up >= 5 and lengths[0] / runner_up >= 1.25


class Report:
    def __init__(self, top: int) -> None:
        self.top = top
        self.warnings: list[str] = []

    def section(self, title: str) -> None:
        print(f"\n{title}")
        print("-" * len(title))

    def warn(self, message: str) -> None:
        self.warnings.append(message)


def pool_key(row: dict[str, str]) -> tuple[str, str]:
    return (row["category"], row["difficulty"])


def analyze(rows: list[dict[str, str]], report: Report) -> None:
    text_rows = [row for row in rows if row["category"] != FLAG_CATEGORY]
    flag_rows = [row for row in rows if row["category"] == FLAG_CATEGORY]

    pools: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    for row in text_rows:
        pools[pool_key(row)].append(row)

    # --- Inventory -------------------------------------------------------
    report.section("Inventory")
    categories = sorted({row["category"] for row in rows})
    print(f"{len(rows)} questions: {len(text_rows)} text, {len(flag_rows)} flags")
    print(f"{len(categories)} categories, {len(pools)} text pools")
    sizes = Counter(len(pool) for pool in pools.values())
    for size, count in sorted(sizes.items()):
        print(f"  {count} pools of {size} questions")
    # Each difficulty has its own intended depth — easy and medium carry fifty,
    # hard forty — so an outlier is measured against its own tier, not the bank.
    by_difficulty = defaultdict(Counter)
    for key, pool in pools.items():
        by_difficulty[key[1]][len(pool)] += 1
    for key, pool in sorted(pools.items()):
        expected = by_difficulty[key[1]].most_common(1)[0][0]
        if len(pool) != expected:
            report.warn(
                f"{key[0]} / {key[1]} has {len(pool)} questions, "
                f"off the usual {expected} for that difficulty"
            )

    # --- Malformed -------------------------------------------------------
    report.section("Structural integrity")
    problems = 0
    ids = Counter(row["id"] for row in rows)
    for question_id, count in ids.items():
        if count > 1:
            report.warn(f"duplicate id {question_id} appears {count} times")
            problems += 1
    for row in rows:
        answers = answers_of(row)
        if len(set(answers)) != 4:
            report.warn(f"{row['id']} does not have four distinct choices")
            problems += 1
        if correct_index(row) is None:
            report.warn(f"{row['id']} correct answer is not one of its choices")
            problems += 1
    prompts = Counter(row["prompt"] for row in text_rows)
    for prompt, count in prompts.items():
        if count > 1:
            report.warn(f"prompt repeated {count} times: {prompt[:70]}")
            problems += 1
    print(f"{problems} structural problems")

    # --- Length bias -----------------------------------------------------
    report.section(f"Longest-answer bias (pool limit {POOL_LONGEST_LIMIT:.0%}, matches the Swift gate)")
    rates = []
    for key, pool in pools.items():
        rate = sum(longest_answer_credit(row) for row in pool) / len(pool)
        rates.append((rate, key))
        if rate > POOL_LONGEST_LIMIT:
            report.warn(f"{key[0]} / {key[1]} longest-answer rate {rate:.1%} exceeds {POOL_LONGEST_LIMIT:.0%}")
    rates.sort(reverse=True)
    overall = sum(longest_answer_credit(row) for row in text_rows) / len(text_rows)
    sole = sum(is_sole_longest(row) for row in text_rows) / len(text_rows)
    print(f"bank-wide {overall:.1%}   (chance alone is ~25%; sole-longest only {sole:.1%})")
    for rate, key in rates[: report.top]:
        print(f"  {rate:5.1%}  {key[0]} / {key[1]}")

    # --- Conspicuous outliers -------------------------------------------
    report.section(f"Conspicuous giveaways (pool budget {CONSPICUOUS_POOL_LIMIT}, bank {CONSPICUOUS_RATE_LIMIT:.0%})")
    counts = []
    for key, pool in pools.items():
        count = sum(is_conspicuous(row) for row in pool)
        counts.append((count, key))
        if count > CONSPICUOUS_POOL_LIMIT:
            report.warn(f"{key[0]} / {key[1]} has {count} conspicuous giveaways, over budget {CONSPICUOUS_POOL_LIMIT}")
    counts.sort(reverse=True)
    total = sum(count for count, _ in counts)
    rate = total / len(text_rows)
    print(f"{total} of {len(text_rows)} text questions ({rate:.1%})")
    if rate > CONSPICUOUS_RATE_LIMIT:
        report.warn(f"bank-wide conspicuous rate {rate:.1%} exceeds {CONSPICUOUS_RATE_LIMIT:.0%}")
    for count, key in counts[: report.top]:
        print(f"  {count:3d}  {key[0]} / {key[1]}")

    # --- Repeated keyed answers -----------------------------------------
    report.section(f"Repeated correct answers (limit {KEYED_ANSWER_REPEAT_LIMIT} per pool)")
    worst = []
    for key, pool in pools.items():
        grouped = Counter(
            " ".join(sorted(tokens(row["correct_answer"]))) for row in pool
        )
        answer, count = grouped.most_common(1)[0]
        worst.append((count, key, answer))
        for repeated, repeat_count in grouped.items():
            if repeat_count > KEYED_ANSWER_REPEAT_LIMIT:
                report.warn(f"{key[0]} / {key[1]} keys '{repeated}' {repeat_count} times")
    worst.sort(reverse=True)
    for count, key, answer in worst[: report.top]:
        print(f"  {count:3d}x '{answer[:40]}'  {key[0]} / {key[1]}")

    # --- Explanation reinforcement ---------------------------------------
    report.section(f"Explanations naming the answer (floor {REINFORCEMENT_FLOOR:.0%})")
    per_pool = []
    for key, pool in pools.items():
        hits = sum(
            1
            for row in pool
            if tokens(row["correct_answer"]) & tokens(row["explanation"])
        )
        per_pool.append((hits / len(pool), key))
    per_pool.sort()
    bank = sum(
        1 for row in text_rows if tokens(row["correct_answer"]) & tokens(row["explanation"])
    ) / len(text_rows)
    print(f"bank-wide {bank:.1%}")
    if bank < REINFORCEMENT_FLOOR:
        report.warn(f"bank-wide reinforcement {bank:.1%} below floor {REINFORCEMENT_FLOOR:.0%}")
    for value, key in per_pool[: report.top]:
        print(f"  {value:5.1%}  {key[0]} / {key[1]}")

    # --- Near-duplicate prompts ------------------------------------------
    report.section("Near-duplicate prompts (>=80% token overlap, same category)")
    by_category: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in text_rows:
        by_category[row["category"]].append(row)
    pairs = []
    for category, group in by_category.items():
        cached = [(row, tokens(row["prompt"])) for row in group]
        for i in range(len(cached)):
            row_a, tokens_a = cached[i]
            if not tokens_a:
                continue
            for j in range(i + 1, len(cached)):
                row_b, tokens_b = cached[j]
                if not tokens_b:
                    continue
                overlap = len(tokens_a & tokens_b) / len(tokens_a | tokens_b)
                if overlap >= 0.80:
                    pairs.append((overlap, category, row_a, row_b))
    pairs.sort(reverse=True, key=lambda item: item[0])
    print(f"{len(pairs)} near-duplicate pairs")
    for overlap, category, row_a, row_b in pairs[: report.top]:
        print(f"  {overlap:.0%} {category}")
        print(f"      {row_a['id']}: {row_a['prompt'][:66]}")
        print(f"      {row_b['id']}: {row_b['prompt'][:66]}")
    if pairs:
        report.warn(f"{len(pairs)} near-duplicate prompt pairs need review")

    # --- Same answer, reworded prompt -------------------------------------
    # Token overlap alone misses the duplicate that matters most to a player:
    # the same fact asked twice in different words. "Which lake holds the most
    # fresh water by volume?" and "Which lake holds the largest volume of fresh
    # water?" share only 67% of their tokens and key the same answer. Pairing
    # an identical keyed answer with a looser prompt threshold catches those.
    report.section("Same answer asked twice (identical key, >=40% prompt overlap)")
    restated = []
    for category, group in by_category.items():
        by_answer: dict[str, list[dict[str, str]]] = defaultdict(list)
        for row in group:
            key = " ".join(sorted(tokens(row["correct_answer"])))
            if key:
                by_answer[key].append(row)
        for key, rows_with_answer in by_answer.items():
            for i in range(len(rows_with_answer)):
                for j in range(i + 1, len(rows_with_answer)):
                    row_a, row_b = rows_with_answer[i], rows_with_answer[j]
                    tokens_a, tokens_b = tokens(row_a["prompt"]), tokens(row_b["prompt"])
                    if not tokens_a or not tokens_b:
                        continue
                    overlap = len(tokens_a & tokens_b) / len(tokens_a | tokens_b)
                    if overlap >= 0.40:
                        restated.append((overlap, category, row_a, row_b))
    restated.sort(reverse=True, key=lambda item: item[0])
    print(f"{len(restated)} restated pairs")
    for overlap, category, row_a, row_b in restated[: report.top]:
        print(f"  {overlap:.0%} {category} -> {row_a['correct_answer'][:40]}")
        print(f"      {row_a['id']}: {row_a['prompt'][:66]}")
        print(f"      {row_b['id']}: {row_b['prompt'][:66]}")
    if restated:
        report.warn(f"{len(restated)} questions restate another question's answer")

    # --- Copy length ------------------------------------------------------
    report.section("Copy length (house style: prompt <=20 words, explanation 8-24)")
    long_prompts = [row for row in text_rows if len(row["prompt"].split()) > 20]
    short_explanations = [row for row in text_rows if len(row["explanation"].split()) < 8]
    long_explanations = [row for row in text_rows if len(row["explanation"].split()) > 24]
    long_answers = [
        row for row in text_rows if any(len(a.split()) > 12 for a in answers_of(row))
    ]
    print(f"{len(long_prompts)} prompts over 20 words")
    print(f"{len(short_explanations)} explanations under 8 words")
    print(f"{len(long_explanations)} explanations over 24 words")
    print(f"{len(long_answers)} questions with an answer over 12 words")
    for row in (long_prompts + long_answers)[: report.top]:
        print(f"  {row['id']}: {row['prompt'][:70]}")

    # --- Authored answer position ----------------------------------------
    # Runtime shuffles every round, so this cannot leak to a player. It is a
    # health signal about how the source files were written, and a lopsided
    # distribution usually means a category was authored on autopilot.
    report.section("Authored answer position (shuffled at runtime; source hygiene only)")
    for category in sorted({row["category"] for row in text_rows}):
        positions = Counter(
            correct_index(row)
            for row in text_rows
            if row["category"] == category and correct_index(row) is not None
        )
        total_in_category = sum(positions.values())
        spread = " ".join(
            f"{'ABCD'[position]}:{positions.get(position, 0) / total_in_category:4.0%}"
            for position in range(4)
        )
        skew = max(positions.values()) / total_in_category
        marker = "  <-- lopsided" if skew >= 0.60 else ""
        print(f"  {category:<22} {spread}{marker}")

    # --- Review deadlines -------------------------------------------------
    report.section("Scheduled fact re-verification")
    dated = [row for row in rows if row.get("review_after")]
    print(f"{len(dated)} questions carry a source and review deadline")
    for row in sorted(dated, key=lambda item: item["review_after"])[: report.top]:
        print(f"  {row['review_after']}  {row['id']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--top", type=int, default=8, help="rows to show per section")
    parser.add_argument("--fail-on-warn", action="store_true", help="exit non-zero if warnings")
    args = parser.parse_args()

    rows = load_rows()
    report = Report(top=args.top)
    print(f"EZ Trivia question bank -- {len(rows)} rows from {CSV_PATH.name}")
    analyze(rows, report)

    report.section("Warnings")
    if report.warnings:
        for warning in report.warnings:
            print(f"  ! {warning}")
    else:
        print("  none")

    return 1 if args.fail_on_warn and report.warnings else 0


if __name__ == "__main__":
    raise SystemExit(main())
