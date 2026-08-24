# EZTrivia

EZTrivia is a small, native iPhone trivia game built with SwiftUI. Pick a category, answer a ten-question round, and either continue with ten more or head back home. The opening screen includes a reserved banner-ad placement while gameplay stays distraction-free.

## Highlights

- Eleven launch categories: Football, Basketball, Soccer, World Flags, History, Science, Movies, Geography, Music, Animals, and Food & Drink
- Visual flag-identification questions with four country choices
- Ten-question rounds with immediate answer feedback and short explanations
- Optional endless follow-up rounds, with unseen questions preferred
- Easy, medium, and hard modes with 50 questions per category at each difficulty
- Local best scores and a lightweight on-device leaderboard
- Dark-mode support, Dynamic Type-friendly layouts, and VoiceOver labels
- No third-party dependencies

## Open in Xcode

1. Open `EZTrivia.xcodeproj` in Xcode 16 or newer.
2. Select an iPhone simulator and run the `EZTrivia` scheme.

The deployment target is iOS 17.0. The home screen contains a Google AdMob banner integration with User Messaging Platform consent; it stays in a non-networking setup state until valid account identifiers are supplied.

Flag questions use a bundled local catalog of all 249 ISO 3166-1 country and territory flags, so flag rounds work without a network connection.

## Suggested next steps

1. **Review the content:** fact-check and copy-edit every difficulty catalog, then replace generated variants with additional hand-authored facts as the library grows.
2. **Verify flag licensing and names:** review official-symbol rules and user-facing territory names before commercial distribution.
3. **Finish service provisioning:** supply the publishing bundle/team IDs, AdMob identifiers, Firebase configuration, and App Store Connect Game Center leaderboards described below.
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
- `Tests/EZTriviaCoreTests/` — engine tests

## Production service setup

The source integrations are complete, but Apple and Google require account-specific identifiers and configuration before live services can work.

### AdMob banner and Firebase

1. The Release target is configured with the supplied AdMob app and banner identifiers; Debug uses Google's official test identifiers.
2. Every banner request explicitly sets `npa=1`, so this release requests non-personalized ads only.
3. Firebase is optional. Without `GoogleService-Info.plist`, Firebase stays disabled; Apple App Analytics and Xcode Organizer crash reports can support the initial release.
4. Configure and publish AdMob privacy messages for the United States and Canada, then complete the App Store privacy labels.

Firebase remains disabled safely when `GoogleService-Info.plist` is absent and is not required for release. See [`RELEASE_SETUP.md`](RELEASE_SETUP.md) for the complete Apple, Game Center, AdMob, Firebase, age-rating, storefront, and submission walkthrough.

### Game Center

1. The Xcode target now uses explicit bundle ID `EZTrivia`, display name **EZ Trivia**, and Apple Team ID `3564X3VTDB`.
2. Enable Game Center for that App ID in Certificates, Identifiers & Profiles and for the app record in App Store Connect.
3. Create eleven classic leaderboards configured for a **high score** from 0 through 100 using IDs `EZTrivia.<category>`, as listed in `RELEASE_SETUP.md`.
4. Add only the required default English (U.S.) display metadata—no translated localizations—and test authentication/submission with sandbox Game Center accounts on physical devices.

EZTrivia authenticates at launch, submits the completed round percentage to its category leaderboard, and opens Apple's native Game Center leaderboard dashboard from the Scores tab.

## Release assets and privacy

- `Assets.xcassets/AppIcon.appiconset` contains the production-sized 1024×1024 app icon.
- `Assets.xcassets/LaunchArtwork.imageset` and `LaunchScreen.storyboard` provide launch artwork.
- `PrivacyInfo.xcprivacy` declares the first-party analytics and crash data categories.
- The public-facing policy is in [`PRIVACY.md`](PRIVACY.md). Review the final advertising consent behavior and App Store privacy answers with qualified counsel before release.
