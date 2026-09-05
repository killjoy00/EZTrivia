#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def swift(value: str) -> str:
    return value.replace('\\', '\\\\').replace('"', '\\"')


def replace_seed(path: str, old_prompt: str, new_prompt: str, answers: list[str], correct: int, explanation: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    pattern = re.compile(
        r'        QuestionSeed\("' + re.escape(old_prompt) + r'",\n'
        r'                     \[[^\n]*\], \d+,\n'
        r'                     "[^\n]*"\),'
    )
    match = pattern.search(text)
    if match is None:
        raise SystemExit(f"missing seed in {path}: {old_prompt}")
    if len(pattern.findall(text)) != 1:
        raise SystemExit(f"non-unique seed in {path}: {old_prompt}")
    choices = ", ".join(f'"{swift(answer)}"' for answer in answers)
    replacement = (
        f'        QuestionSeed("{swift(new_prompt)}",\n'
        f'                     [{choices}], {correct},\n'
        f'                     "{swift(explanation)}"),'
    )
    target.write_text(text[:match.start()] + replacement + text[match.end():], encoding="utf-8")


# Ambiguity / factual precision.
replace_seed(
    "Sources/EZTriviaCore/Questions/AnimalsQuestions.swift",
    "Which reptile can shed and regrow its tail to escape predators?",
    "What is autotomy, a defense used by many lizards?",
    [
        "Deliberately shedding a body part",
        "Inflating the body to look larger",
        "Changing skin color to match surroundings",
        "Playing dead until danger passes",
    ],
    0,
    "Autotomy lets an animal deliberately shed a tail or other body part, distracting a predator while it escapes.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/AnimalsQuestions.swift",
    "What is a marsupial's distinguishing feature?",
    "What is unusual about the way most marsupials are born?",
    [
        "Their young are extremely undeveloped",
        "Their young hatch from hard-shelled eggs",
        "Their young can walk within minutes",
        "Their young are born with a full coat",
    ],
    0,
    "Most marsupials give birth after a short pregnancy to tiny, undeveloped young that continue growing while attached to a teat.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/ArtQuestions.swift",
    "Which two primary colors are mixed to make green?",
    "In traditional paint mixing, which two primary colors make green?",
    ["Blue and yellow", "Red and blue", "Red and yellow", "Black and white"],
    0,
    "In the traditional red-yellow-blue model used for paint, blue and yellow pigments combine to make green.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/ArtQuestions.swift",
    "Which artist wrapped the Reichstag and the Pont Neuf in fabric?",
    "Which artist duo wrapped the Reichstag and the Pont Neuf in fabric?",
    [
        "Christo and Jeanne-Claude",
        "Gilbert and George",
        "Bernd and Hilla Becher",
        "Claes Oldenburg and Coosje van Bruggen",
    ],
    0,
    "Christo and Jeanne-Claude wrapped the Reichstag and Pont Neuf in fabric as monumental temporary public artworks.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/BasketballQuestions.swift",
    "How many free throws follow a foul on a three-point shot attempt?",
    "How many free throws follow a foul on a missed three-point shot attempt?",
    ["One", "Two", "Three", "Four or more"],
    2,
    "A shooter fouled on a missed three-pointer receives three free throws; a made shot instead produces one additional free throw.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/FoodQuestions.swift",
    "Which animal's milk is used in traditional Greek feta?",
    "Which animal's milk must make up most or all traditional Greek feta?",
    ["Sheep", "Cows", "Camels", "Buffalo"],
    0,
    "Traditional Greek feta is made from sheep milk, with goat milk allowed only as a smaller part of the blend.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/GeographyQuestions.swift",
    "Which capital city sits at the highest elevation?",
    "Which city is the world's highest seat of national government?",
    ["La Paz", "Quito", "Kathmandu", "Mexico City"],
    0,
    "La Paz, Bolivia's seat of government, sits above 3,600 meters; Sucre is Bolivia's constitutional capital.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/HistoryQuestions.swift",
    "Which Egyptian queen ruled alongside Julius Caesar's Rome?",
    "Which Egyptian queen formed a political alliance with Julius Caesar?",
    ["Cleopatra", "Nefertiti", "Hatshepsut", "Queen Berenice"],
    0,
    "Cleopatra was the last active ruler of Ptolemaic Egypt and formed a political and personal alliance with Julius Caesar.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/HistoryQuestions.swift",
    "Which country first granted women the national vote?",
    "Which self-governing country first granted women the vote in national elections?",
    ["New Zealand", "Australia", "The United Kingdom", "Norway"],
    0,
    "New Zealand granted women the national vote in 1893, ahead of every other self-governing country.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/MythologyQuestions.swift",
    "What is a vampire traditionally said to avoid?",
    "In many modern vampire stories, what is dangerous to vampires?",
    ["Sunlight", "Rainfall", "Music", "Iron tools"],
    0,
    "Sunlight is dangerous or fatal to vampires in many modern stories, though that rule is not universal in older folklore.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/SoccerQuestions.swift",
    "What is awarded when an attacker is fouled inside the penalty area?",
    "What is awarded for a direct-free-kick foul by a defender inside their own penalty area?",
    ["A penalty kick", "A corner kick", "An indirect free kick", "A dropped ball restart"],
    0,
    "A penalty kick is awarded when a defender commits a direct-free-kick offence inside their own penalty area.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/TelevisionQuestions.swift",
    "What is the first episode of a television series called?",
    "What is a test episode made to demonstrate a proposed television series called?",
    ["The pilot", "The finale", "The teaser", "The reunion"],
    0,
    "A pilot is made to demonstrate a proposed series and help a network or distributor decide whether to order more episodes.",
)

# Better distractors: replace invented or obviously unrelated choices with nearby real concepts.
replace_seed(
    "Sources/EZTriviaCore/Questions/BasketballQuestions.swift",
    "What is the NBA championship series called?",
    "What is the NBA championship series called?",
    ["The NBA Finals", "The Conference Finals", "The NBA Cup Final", "The Play-In Tournament"],
    0,
    "The NBA Finals decide the league champion each season in a best-of-seven series.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/BasketballQuestions.swift",
    "What is the NBA's midseason exhibition of its best players called?",
    "What is the NBA's midseason exhibition of its best players called?",
    ["The All-Star Game", "The Rising Stars game", "The NBA Cup Final", "The Summer League championship"],
    0,
    "The All-Star Game brings together top players selected from the league during the regular season.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/BasketballQuestions.swift",
    "What does a coach call to stop play and instruct the team?",
    "What does a coach request to stop play and instruct the team?",
    ["A timeout", "A coach's challenge", "A substitution", "A jump ball"],
    0,
    "A timeout stops play so a coach can instruct the team, make substitutions, and give players a brief rest.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/FootballQuestions.swift",
    "What is the NFL's annual all-star game called?",
    "Which NFL team has a lightning-bolt logo on its helmets?",
    ["The Los Angeles Chargers", "The Tennessee Titans", "The Los Angeles Rams", "The Indianapolis Colts"],
    0,
    "The Los Angeles Chargers use a lightning bolt as the central element of their helmet and team branding.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/MusicQuestions.swift",
    "What is written notation of a piece of music called?",
    "What do performers commonly call printed notation for a piece of music?",
    ["Sheet music", "A setlist", "A libretto", "Liner notes"],
    0,
    "Sheet music prints notes and other musical directions on a staff so performers can read and play the piece.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/MusicQuestions.swift",
    "What is a group of singers performing together called?",
    "What is a large organized group of singers performing together called?",
    ["A choir", "A quartet", "A troupe", "A rhythm section"],
    0,
    "A choir is an organized group of singers performing together, often divided into several vocal parts.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/MusicQuestions.swift",
    "Which instrument is played by striking it with sticks?",
    "Which instrument typically has a stretched membrane over a hollow shell?",
    ["The drum", "The flute", "The harp", "The clarinet"],
    0,
    "A drum produces sound when its stretched membrane is struck by sticks, mallets, or hands.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/ScienceQuestions.swift",
    "What is frozen water called?",
    "What happens to liquid water's volume when it freezes into ice?",
    ["It expands", "It contracts", "It stays exactly the same", "It splits into gases"],
    0,
    "Water expands as it freezes because its molecules form an open crystal structure, which is why ice floats.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/TelevisionQuestions.swift",
    "What does syndication mean for a television series?",
    "What does syndication usually do with an existing television series?",
    [
        "Licenses episodes to other stations or outlets",
        "Moves production to another country",
        "Combines it with a rival network",
        "Recasts every regular role",
    ],
    0,
    "Syndication licenses a series to outlets beyond its original network or distributor, often for rerun packages.",
)
replace_seed(
    "Sources/EZTriviaCore/Questions/TelevisionQuestions.swift",
    "What is syndication in television?",
    "What did television 'sweeps' periods traditionally measure?",
    ["Local audience viewing", "Camera movement speed", "Script reading pace", "Broadcast signal strength"],
    0,
    "Sweeps used Nielsen viewing diaries during selected periods to estimate local audiences and help set advertising rates.",
)

# Freshen the explanation on the newly added population-ranking question.
replace_seed(
    "Sources/EZTriviaCore/Questions/GeographyQuestions.swift",
    "Which African country has the largest population?",
    "Which African country has the largest population?",
    ["Nigeria", "Ethiopia", "Egypt", "South Africa"],
    0,
    "United Nations population estimates place Nigeria as Africa's most populous country, with well over 200 million people.",
)

# Add scheduled review metadata for the new volatile/rules-based rows.
metadata_path = ROOT / "Sources/EZTriviaCore/QuestionReviewMetadata.swift"
metadata = metadata_path.read_text(encoding="utf-8")
for needle, replacement in [
    (
        '        "basketball-medium-8": .init(sourceURL: nbaRules, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),',
        '        "basketball-easy-45": .init(sourceURL: nbaRules, verifiedOn: "2026-09-04", reviewAfter: "2027-07-01"),\n'
        '        "basketball-medium-8": .init(sourceURL: nbaRules, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),',
    ),
    (
        '        "soccer-easy-12": .init(sourceURL: ifabFouls, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),',
        '        "soccer-easy-12": .init(sourceURL: ifabFouls, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),\n'
        '        "soccer-easy-49": .init(sourceURL: ifabFouls, verifiedOn: "2026-09-04", reviewAfter: "2027-07-01"),',
    ),
    (
        '        "geography-easy-9": .init(sourceURL: "https://population.un.org/wpp/", verifiedOn: "2026-08-31", reviewAfter: "2027-07-15"),',
        '        "geography-easy-9": .init(sourceURL: "https://population.un.org/wpp/", verifiedOn: "2026-08-31", reviewAfter: "2027-07-15"),\n'
        '        "geography-medium-42": .init(sourceURL: "https://population.un.org/wpp/", verifiedOn: "2026-09-04", reviewAfter: "2027-07-15"),',
    ),
]:
    if metadata.count(needle) != 1:
        raise SystemExit(f"metadata insertion point missing/non-unique: {needle}")
    metadata = metadata.replace(needle, replacement)
metadata_path.write_text(metadata, encoding="utf-8")

# Keep the exact registry-ID assertion synchronized and make future population-rank additions self-policing.
test_path = ROOT / "Tests/EZTriviaCoreTests/QuestionQualityTests.swift"
tests = test_path.read_text(encoding="utf-8")
for needle, replacement in [
    ('        "basketball-medium-7",', '        "basketball-easy-45", "basketball-medium-7",'),
    ('        "soccer-easy-12",', '        "soccer-easy-12", "soccer-easy-49",'),
    ('        "geography-easy-9",', '        "geography-easy-9", "geography-medium-42",'),
]:
    if tests.count(needle) != 1:
        raise SystemExit(f"test insertion point missing/non-unique: {needle}")
    tests = tests.replace(needle, replacement)

marker = '@Test func timeSensitiveQuestionReviewsAreCurrent() throws {'
new_test = '''@Test func populationRankingQuestionsHaveReviewMetadata() {
    let volatilePhrases = ["largest population", "most populous"]
    for question in QuestionBank.all where question.category != .flags {
        let copy = (question.prompt + " " + question.explanation).lowercased()
        guard volatilePhrases.contains(where: copy.contains) else { continue }
        #expect(QuestionReviewRegistry.byQuestionID[question.id] != nil,
                "\\(question.id) is a population-ranking fact without scheduled review metadata")
    }
}

'''
if tests.count(marker) != 1:
    raise SystemExit("time-sensitive test marker missing/non-unique")
tests = tests.replace(marker, new_test + marker)
test_path.write_text(tests, encoding="utf-8")

# Surface the same population-ranking gap in the human-readable analyzer.
analyzer_path = ROOT / "Scripts/analyze_questions.py"
analyzer = analyzer_path.read_text(encoding="utf-8")
copy_marker = '    # --- Copy length ------------------------------------------------------\n'
coverage = '''    # --- Time-sensitive population rankings -------------------------------
    report.section("Population-ranking review coverage")
    volatile_phrases = ("largest population", "most populous")
    uncovered = []
    for row in text_rows:
        copy = f"{row['prompt']} {row['explanation']}".lower()
        if any(phrase in copy for phrase in volatile_phrases) and not row["review_after"]:
            uncovered.append(row)
    print(f"{len(uncovered)} population-ranking questions without scheduled review metadata")
    for row in uncovered[: report.top]:
        print(f"  {row['id']}: {row['prompt']}")
    if uncovered:
        report.warn(f"{len(uncovered)} population-ranking questions need scheduled review metadata")

'''
if analyzer.count(copy_marker) != 1:
    raise SystemExit("analyzer copy-length marker missing/non-unique")
analyzer = analyzer.replace(copy_marker, coverage + copy_marker)
analyzer_path.write_text(analyzer, encoding="utf-8")

print("Applied question-bank editorial cleanup.")
