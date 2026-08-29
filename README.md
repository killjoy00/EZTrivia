# EZTrivia

EZTrivia is a small, native iPhone trivia game built with SwiftUI. Pick a category, answer a ten-question round, and either continue with ten more or head back home. The opening screen includes a reserved banner-ad placement while gameplay stays distraction-free.

## Highlights

- Twelve categories: Football, Basketball, Soccer, World Flags, History, Science, Movies, TV, Geography, Music, Animals, and Food & Drink
- Visual flag-identification questions covering 241 distinct flags
- Ten-question rounds with immediate answer feedback and short explanations
- Optional endless follow-up rounds, with unseen questions preferred
- Easy, medium, and hard modes that are genuinely different questions, not the same pool relabelled
- Practice mode: questions you answer wrong are collected, and clearing one requires answering it correctly again
- Local best scores and a lightweight on-device leaderboard
- Dark-mode support, Dynamic Type-friendly layouts, and VoiceOver labels
- Google Mobile Ads for a single banner placement; no analytics or crash-reporting SDK

## Open in Xcode

1. Open `EZTrivia.xcodeproj` in Xcode 16 or newer.
2. Select an iPhone simulator and run the `EZTrivia` scheme.

The deployment target is iOS 17.0. The home screen contains a Google AdMob banner integration with User Messaging Platform consent; it stays in a non-networking setup state until valid account identifiers are supplied.

Flag questions use a bundled local catalog of all 249 ISO 3166-1 country and territory flags, so flag rounds work without a network connection.

## Question catalog

1,561 questions ship with the app:

| | Easy | Medium | Hard |
|---|---|---|---|
| Each of the 11 topic categories | 40 | 40 | 40 |
| World Flags | 50 | 91 | 100 |

Every question is written by hand and appears exactly once. No question is
reused across difficulties, and tests in `Tests/EZTriviaCoreTests` fail the
build if a prompt is ever duplicated or a tier shares questions with another.

Five of the 249 flags are never asked about, because their artwork is
byte-identical to another valid answer: Bouvet Island and Svalbard fly the
Norwegian flag, Saint Martin the French tricolour, the U.S. Minor Outlying
Islands the Stars and Stripes, and Heard Island and the McDonald Islands use
the Australian National Flag. They remain in the catalog so the images ship,
but a question with two visually correct answers is not a question.

Flags that are near-identical at phone size — Egypt and Yemen, Romania and
Chad, the blue-ensign cluster — are never offered as each other's answer
choices. Those pairs were measured from the shipped artwork rather than
guessed; see the `confusable` lists in `Sources/EZTriviaCore/FlagCatalog.swift`.

Answer order is shuffled at runtime, so replaying a question does not put the
correct answer in the same position.

The correct answer is also not identifiable by length. Authored distractors used
to be terser than the answer they sat beside, which made the hard tier 73.8%
answerable by always picking the longest option -- three times chance. 267
distractors were rewritten in 1.0.1 to close that gap; a test now fails the build
if any tier drifts back above 40%.

## Suggested next steps

1. **Review the content:** fact-check and copy-edit every difficulty catalog, then replace generated variants with additional hand-authored facts as the library grows.
2. **Verify flag licensing and names:** review official-symbol rules and user-facing territory names before commercial distribution.
3. **Finish service provisioning:** supply the AdMob identifiers and the App Store Connect Game Center leaderboards described below.
4. **Harden privacy controls:** finalize age rating, child-directed treatment, consent regions, App Tracking Transparency decisions, and a paid ad-free option if desired.
5. **Finish release QA:** add UI tests and localization, run accessibility/device testing, archive on macOS, and collect TestFlight feedback.

## Run core tests

The question and scoring engine is also exposed as a Swift package so it can be tested without Xcode:

```bash
swift test
```

## Review the question catalog

`QuestionReview.csv` contains the complete catalog in a spreadsheet-friendly format. Each row includes the category, difficulty, prompt, optional local visual asset name, four choices, correct answer, and explanation. Open it in Numbers, Excel, Google Sheets, or a text editor and leave feedback using the stable `id` in the first column.

After changing question data, regenerate the review file with:

```bash
swift run QuestionCatalogExporter > QuestionReview.csv
```

Suggested review checks:

- Is the fact and correct answer accurate?
- Are all three incorrect choices plausible but unambiguously wrong?
- Does the assigned difficulty feel right?
- Is the wording clear, inclusive, and likely to remain accurate over time?
- Does the explanation add useful context without simply repeating the answer?

## Project layout

- `EZTriviaApp/` — SwiftUI application, views, and local persistence
- `Sources/EZTriviaCore/` — reusable trivia models, question bank, and round engine
- `Sources/EZTriviaCore/Questions/` — the authored questions, one file per category
- `Sources/EZTriviaCore/FlagCatalog.swift` — flag names, difficulty tiers, and confusable pairs
- `Tests/EZTriviaCoreTests/` — engine tests

## Production service setup

The source integrations are complete, but Apple and Google require account-specific identifiers and configuration before live services can work.

### AdMob banner

1. The Release target is configured with the supplied AdMob app and banner identifiers; Debug uses Google's official test identifiers.
2. Every banner request explicitly sets `npa=1`, so this release requests non-personalized ads only.
4. Configure and publish AdMob privacy messages for the United States and Canada, then complete the App Store privacy labels.

See [`RELEASE_SETUP.md`](RELEASE_SETUP.md) for the complete Apple, Game Center, AdMob, age-rating, storefront, and submission walkthrough, including how builds are produced without a Mac.

### Game Center

1. The Xcode target now uses explicit bundle ID `EZTrivia`, display name **EZ Trivia**, and Apple Team ID `3564X3VTDB`.
2. Enable Game Center for that App ID in Certificates, Identifiers & Profiles and for the app record in App Store Connect.
3. Create twelve classic leaderboards configured for a **high score** from 0 through 100 using IDs `EZTrivia.<category>`, as listed in `RELEASE_SETUP.md`.
4. Add only the required default English (U.S.) display metadata—no translated localizations—and test authentication/submission with sandbox Game Center accounts on physical devices.

EZTrivia authenticates at launch, submits the completed round percentage to its category leaderboard, and opens Apple's native Game Center leaderboard dashboard from the Scores tab.

## Release assets and privacy

- `Assets.xcassets/AppIcon.appiconset` contains the production-sized 1024×1024 app icon.
- `Assets.xcassets/LaunchArtwork.imageset` and `LaunchScreen.storyboard` provide launch artwork.
- `PrivacyInfo.xcprivacy` declares the first-party analytics and crash data categories.
- The public-facing policy is in [`PRIVACY.md`](PRIVACY.md). Review the final advertising consent behavior and App Store privacy answers with qualified counsel before release.
