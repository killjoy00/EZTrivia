# EZ Trivia

EZ Trivia is a native SwiftUI trivia game for iPhone. Players can choose a category, complete the shared Daily Challenge, or create a reproducible ten-question Friend Challenge and send its code to someone else. Gameplay remains offline and distraction-free; the only ad placement is a banner on the Play home screen.

## Highlights

- Sixteen categories, including Mythology & Legends and Video Games
- 2,341 hand-authored questions with Easy, Medium, and Hard tiers
- Ten-question category rounds with immediate feedback and short explanations
- A deterministic Daily Challenge shared by every player, with local streak tracking
- One-attempt Friend Challenges using short, server-free codes that reproduce the same questions and answer order
- Thirteen local achievements that synchronize with Game Center when available
- Native global and friends leaderboards inside the Scores tab, plus optional access to Apple's full Game Center dashboard
- Local recent-round history, category best scores, and difficulty-weighted lifetime points
- Optional 2–15 second auto-advance, sound effects, and haptics in the Settings tab
- Shareable square result cards for category, Daily, and Friend Challenge rounds
- Dark mode, Dynamic Type layouts, and VoiceOver labels
- Google Mobile Ads and User Messaging Platform for one home-screen banner; no gameplay ads, analytics SDK, or crash-reporting SDK

## Open in Xcode

1. Open `EZTrivia.xcodeproj` in Xcode 16 or newer.
2. Select an iPhone simulator and run the `EZTrivia` scheme.

The deployment target is iOS 17.0. Debug builds use Google's official test AdMob identifiers; Release builds use the configured production app and banner identifiers.

## Question catalog

The app ships 2,341 questions:

| Category | Easy | Medium | Hard |
| --- | ---: | ---: | ---: |
| Each of the 15 text categories | 50 | 50 | 40 |
| World Flags | 51 | 91 | 99 |

Every question appears exactly once. Tests fail the build if a prompt is duplicated, a tier shares questions with another, answers are malformed, or a category/difficulty pool no longer holds its expected depth: fifty questions at Easy and Medium, forty at Hard.

`Scripts/analyze_questions.py` reports on the catalog without a Swift toolchain: length bias, giveaway answers, explanations that never name their answer, and prompts that restate a question already in the category. `Scripts/append_seeds.py` authors new questions against those same rules and refuses a seed that reuses a keyed answer the category already has.

The local catalog contains all 249 ISO 3166-1 flag assets. Five are not asked because their artwork is identical to another valid answer, and three more are excluded because the bundled design is contested or no longer current. The remaining 241 flags are askable. Near-identical flags at phone size are never offered against one another, and flag distractors are redrawn from the same difficulty pool each time.

Answer order is shuffled at presentation time. Daily Challenges use seeded selection, while Friend Challenges use a repository-owned seeded algorithm so another device receives the same questions and option order. Version-3 Friend Challenge codes use an explicit sixteen-category roster and carry a checksum, allowing future releases to reject incompatible or mistyped codes instead of silently producing a different round. The Daily roster now spans all sixteen categories. Mythology & Legends and Video Games can appear in a daily round as soon as a player updates; anyone still on the previous build sees the old fourteen-category round in the meantime, so a same-day leaderboard briefly compares two different rounds until the previous build ages out.

The correct answer is not identifiable simply by selecting the longest option. Tests cap the heuristic at 35% in every text pool and separately budget conspicuous per-question length gaps. Additional editorial checks limit repeated keyed answers, measure whether explanations reinforce their answers, enforce concise U.S.-English copy for newly authored categories, and fail when a scheduled rule or record verification becomes overdue.

## Run core tests

The question, scoring, Daily Challenge, and Friend Challenge engines are exposed as a Swift package:

```bash
swift test
```

CI also builds and archives the SwiftUI app because package tests do not compile the iOS interface or GameKit integration.

## Review the question catalog

`QuestionReview.csv` contains the complete reviewable catalog. Each row includes the stable ID, category, difficulty, prompt, optional local visual asset name, four choices, correct answer, explanation, review note, and—where a fact can change—its source and verification deadline.

After changing question data, regenerate it with:

```bash
swift run QuestionCatalogExporter > QuestionReview.csv
```

Useful review checks:

- Is the fact and correct answer accurate?
- Are all three incorrect choices plausible but unambiguously wrong?
- Does the assigned difficulty feel right?
- Is the wording clear, inclusive, and likely to remain accurate?
- Does the explanation add context rather than merely repeat the answer?

## Production services

### AdMob banner

The banner implementation and identifiers are already present in source:

- Debug app ID and banner ID use Google's official test values.
- Release uses production app ID `ca-app-pub-3388571830343061~4987263013` and banner ID `ca-app-pub-3388571830343061/4408678515`.
- `AdConsentManager` completes the User Messaging Platform flow before `AdBannerView` requests an ad.
- The banner is pinned above the tab bar on the Play home screen and disappears during gameplay.

There is no additional code switch to turn it on. Live display requires the matching iOS app and banner unit to be active in AdMob, the applicable privacy message to be published, and a Release/TestFlight build with ad inventory available. See `RELEASE_SETUP.md` for the account and device checklist.

### Game Center

The bundle identifier is `com.rsm.eztrivia`, and the target contains the Game Center entitlement. Category leaderboards rank monotonic lifetime points; the Daily leaderboard ranks the day's weighted score. `Scripts/configure_game_center.py` idempotently provisions the seventeen leaderboards during an authenticated TestFlight archive.

The thirteen achievement definitions are mirrored in `EZTriviaApp/Achievements.swift` and `RELEASE_SETUP.md`. Required 1024×1024 artwork is checked into `Artwork/Achievements`. App Store Connect achievements must be created and submitted for review before Game Center will accept progress reports; local progress works regardless.

## Project layout

- `EZTriviaApp/` — SwiftUI views, settings, local persistence, AdMob, sharing, and GameKit integration
- `Sources/EZTriviaCore/` — reusable models, question bank, scoring, Daily Challenge, and Friend Challenge engines
- `Sources/EZTriviaCore/Questions/` — hand-authored text questions, one file per category
- `Tests/EZTriviaCoreTests/` — deterministic engine and content-quality tests
- `Artwork/Achievements/` — production-sized Game Center achievement artwork
- `QuestionReview.csv` — spreadsheet-friendly content review export
- `RELEASE_SETUP.md` — cloud build, App Store Connect, Game Center, AdMob, and TestFlight setup

## Privacy

The public policy is in `PRIVACY.md`. `PrivacyInfo.xcprivacy` declares the app's first-party data categories. Review final advertising consent behavior and App Store privacy answers with qualified counsel before release.
