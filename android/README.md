# EZ Trivia Android

This directory contains the native Android client. The iOS SwiftUI app remains
unchanged in `EZTriviaApp/`.

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

The Android client now ports the shared domain models, weighted scoring,
round-engine behavior, deterministic RNG primitive, Friend Challenge v3 code
format and exact round construction. Category rounds, Quick Play, Scores/history,
local achievement facts, result-card sharing, and Friend Challenges are playable
offline.

Friend Challenge v3 deliberately uses repository-owned deterministic algorithms
for category order, question selection, answer order, and flag distractor draws.
An iOS-created `EZ3` code therefore maps to the same questions and choices on
Android. The code's attempt identity is version + seed, so altering a claimed
target score or point total cannot create a second attempt at the same round.
Android accepts both `eztrivia://challenge/...` links and the public GitHub Pages
challenge handoff used by iOS sharing.

Player state is stored with Preferences DataStore. Completed Friend Challenges
are locally one-attempt, survive the recent-category-history clear action, count
toward durable round/category achievement facts, and intentionally do not add to
category lifetime leaderboard points—matching the iOS scoring contract.

## CI

The repository's `android-ci.yml` workflow installs Gradle 9.6, generates the
question asset, runs unit tests, lint, and assembles the debug APK. No local
Android workstation is required for this workflow. The generated APK is uploaded
as the `eztrivia-android-debug` artifact.

## Cross-platform boundary

Friend Challenge v3 is frozen around explicit repository-owned algorithms and is
safe for exact Swift/Kotlin parity. Daily Challenge still relies on seeded Swift
standard-library selection/shuffle behavior in parts of its current contract, so
Android should not claim identical Daily rounds until that algorithm is likewise
frozen and versioned across both clients.
