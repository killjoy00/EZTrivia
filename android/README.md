# EZ Trivia Android

This directory contains the native Android client. The iOS SwiftUI app remains
in `EZTriviaApp/`, with shared deterministic game contracts owned by
`Sources/EZTriviaCore/`.

## Foundation contract

- Kotlin + Jetpack Compose UI
- application ID `com.rsm.eztrivia`
- min SDK 26, target/compile SDK 36
- the Swift-authored question bank remains the source of truth
- `QuestionReview.csv` is already regenerated and verified by Swift CI
- `Scripts/export_android_catalog.py` converts that verified catalog into the
  Android asset before every build
- the exporter reconstructs `QuestionBank.all` ordering so seeded modes can use
  the same bank order on both platforms
- flag questions also carry the Swift `FlagCatalog` confusability metadata that
  deterministic cross-platform modes need to redraw the same answer choices

The Android client ports the shared domain models, weighted scoring, round-engine
behavior, deterministic RNG primitive, Friend Challenge v3, and Daily Challenge
v2. Category rounds, Quick Play, Daily, Scores/history, local achievement facts,
result-card sharing, and Friend Challenges are playable offline.

## Deterministic cross-platform modes

Friend Challenge v3 deliberately uses repository-owned deterministic algorithms
for category order, question selection, answer order, and flag distractor draws.
An iOS-created `EZ3` code therefore maps to the same questions and choices on
Android. The code's attempt identity is version + seed, so altering a claimed
target score or point total cannot create a second attempt at the same round.
Android accepts both `eztrivia://challenge/...` links and the public GitHub Pages
challenge handoff used by iOS sharing.

Daily Challenge v2 uses the same repository-owned deterministic primitives and a
frozen sixteen-category roster. It begins with internal day 251, displayed as
**Daily #252 on September 9, 2026**. iOS keeps its already-shipped legacy Swift
selection algorithm for earlier historical days, so adding Android does not
rewrite a Daily that players may already have completed. Android does not claim
parity for those pre-v2 historical rounds.

Swift and Kotlin tests pin the same full-round fingerprints for multiple Daily
v2 dates. Each fingerprint covers the ten question IDs, every answer's exact
position, and every correct-answer index, so catalog or algorithm drift fails CI
instead of silently serving different rounds to iOS and Android players.

Player state is stored with Preferences DataStore. Completed Daily and Friend
Challenge results are locally one-attempt, survive the recent-category-history
clear action, count toward durable round/category achievement facts, and
intentionally do not add to category lifetime leaderboard points—matching the
iOS scoring contract. Daily streaks remain alive through the first unplayed day
and break only after a full local calendar day is missed, matching iOS.

## CI

The repository's `android-ci.yml` workflow installs Gradle 9.6, generates the
question asset, runs unit tests, lint, and assembles the debug APK. No local
Android workstation is required for this workflow. The generated APK is uploaded
as the `eztrivia-android-debug` artifact.
