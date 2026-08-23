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

The deployment target is iOS 17.0. The ad on the home screen is intentionally a first-party placeholder (`AdBannerView`); replace it with your chosen ad network and consent flow before release.

Flag questions are built from the ISO 3166-1 country database included with Debian's `iso-codes` project and display flag images from `flagcdn.com`. Flag rounds therefore require a network connection; the app shows a clear fallback if an image cannot load.

## Suggested next steps

1. **Review the content:** fact-check and copy-edit every difficulty catalog, then replace generated variants with additional hand-authored facts as the library grows.
2. **Bundle flag artwork:** the current version uses FlagCDN. Before release, consider licensed or original local flag assets for offline play and identical rendering on every device.
3. **Choose leaderboard scope:** keep the current private on-device scores, or add Game Center for authenticated global and friend leaderboards.
4. **Add the production ad provider:** replace the placeholder only after implementing privacy consent, age gating, and a paid ad-free option if desired.
5. **App Store readiness:** add an app icon, launch artwork, privacy policy, analytics/crash reporting, UI tests, localization, and TestFlight feedback.

## Run core tests

The question and scoring engine is also exposed as a Swift package so it can be tested without Xcode:

```bash
swift test
```

## Review the question catalog

`QuestionReview.csv` contains the complete catalog in a spreadsheet-friendly format. Each row includes the category, difficulty, prompt, optional flag URL, four choices, correct answer, and explanation. Open it in Numbers, Excel, Google Sheets, or a text editor and leave feedback using the stable `id` in the first column.

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
