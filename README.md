# EZTrivia

EZTrivia is a small, native iPhone trivia game built with SwiftUI. Pick a category, answer a ten-question round, and either continue with ten more or head back home. The opening screen includes a reserved banner-ad placement while gameplay stays distraction-free.

## Highlights

- Six launch categories: American Football, Soccer, World Flags, History, Science, and Movies
- Visual flag-identification questions with four country choices
- Ten-question rounds with immediate answer feedback and short explanations
- Optional endless follow-up rounds, with unseen questions preferred
- Local best scores and a lightweight on-device leaderboard
- Dark-mode support, Dynamic Type-friendly layouts, and VoiceOver labels
- No third-party dependencies

## Open in Xcode

1. Open `EZTrivia.xcodeproj` in Xcode 16 or newer.
2. Select an iPhone simulator and run the `EZTrivia` scheme.

The deployment target is iOS 17.0. The ad on the home screen is intentionally a first-party placeholder (`AdBannerView`); replace it with your chosen ad network and consent flow before release.

## Suggested next steps

1. **Grow and review the content:** expand each category to at least 100 questions, add difficulty levels, and have every question fact-checked and copy-edited.
2. **Upgrade flag artwork:** the first version uses native flag emoji so it stays dependency-free. Before release, consider licensed or original flag assets for identical rendering on every device.
3. **Choose leaderboard scope:** keep the current private on-device scores, or add Game Center for authenticated global and friend leaderboards.
4. **Add the production ad provider:** replace the placeholder only after implementing privacy consent, age gating, and a paid ad-free option if desired.
5. **App Store readiness:** add an app icon, launch artwork, privacy policy, analytics/crash reporting, UI tests, localization, and TestFlight feedback.

## Run core tests

The question and scoring engine is also exposed as a Swift package so it can be tested without Xcode:

```bash
swift test
```

## Project layout

- `EZTriviaApp/` — SwiftUI application, views, and local persistence
- `Sources/EZTriviaCore/` — reusable trivia models, question bank, and round engine
- `Tests/EZTriviaCoreTests/` — engine tests
