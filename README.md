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

1. In AdMob, create an iOS app using the final bundle identifier and create one **Banner** ad unit.
2. In the target build settings, set `ADMOB_APP_ID` to the AdMob app identifier (`ca-app-pub-…~…`) and `ADMOB_BANNER_ID` to the banner ad-unit identifier (`ca-app-pub-…/…`). These are public app configuration values, not secrets.
3. Register the same iOS app in Firebase, enable Analytics and Crashlytics, download `GoogleService-Info.plist`, and add it to the `EZTrivia` application target. Do not commit a configuration file for a different app or bundle identifier.
4. Configure AdMob privacy messaging for every served region and complete the App Store privacy labels. Production personalized advertising must not be enabled until consent and any required App Tracking Transparency flow have been reviewed.

When either AdMob identifier is absent, the home screen deliberately displays a setup banner and makes no ad request. Firebase also remains disabled when `GoogleService-Info.plist` is absent.

### Game Center

1. Replace the placeholder `com.example.EZTrivia` bundle identifier and select the Apple Developer team that will publish the app.
2. Enable Game Center for that App ID in Certificates, Identifiers & Profiles and for the app record in App Store Connect.
3. Create eleven classic leaderboards configured for a **high score** from 0 through 100, using these exact IDs:
   - `com.killjoy00.eztrivia.football`
   - `com.killjoy00.eztrivia.basketball`
   - `com.killjoy00.eztrivia.soccer`
   - `com.killjoy00.eztrivia.flags`
   - `com.killjoy00.eztrivia.history`
   - `com.killjoy00.eztrivia.science`
   - `com.killjoy00.eztrivia.movies`
   - `com.killjoy00.eztrivia.geography`
   - `com.killjoy00.eztrivia.music`
   - `com.killjoy00.eztrivia.animals`
   - `com.killjoy00.eztrivia.food`
4. Add leaderboard localizations and test authentication/submission with sandbox Game Center accounts on physical devices.

EZTrivia authenticates at launch, submits the completed round percentage to its category leaderboard, and opens Apple's native Game Center leaderboard dashboard from the Scores tab.

## Release assets and privacy

- `Assets.xcassets/AppIcon.appiconset` contains the production-sized 1024×1024 app icon.
- `Assets.xcassets/LaunchArtwork.imageset` and `LaunchScreen.storyboard` provide launch artwork.
- `PrivacyInfo.xcprivacy` declares the first-party analytics and crash data categories.
- The public-facing policy is in [`PRIVACY.md`](PRIVACY.md). Review the final advertising consent behavior and App Store privacy answers with qualified counsel before release.
