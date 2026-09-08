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

The first Android slice ports the shared domain models, weighted scoring,
round-engine behavior, deterministic RNG primitive, and Friend Challenge code
format. The Compose shell loads all 2,341 questions and exposes the full category
roster. Gameplay UI, flag assets, persistence, Daily/Friend round selection,
Play Games, AdMob/UMP and Billing are layered on top in subsequent changes.

## CI

The repository's `android-ci.yml` workflow installs Gradle 9.6, generates the
question asset, runs unit tests, lint, and assembles the debug APK. No local
Android workstation is required for this workflow.
