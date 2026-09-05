# EZ Trivia

EZ Trivia is a native SwiftUI trivia game for iPhone. Players can jump straight into a mixed Quick Play round, choose a category, complete the shared Daily Challenge, or create a reproducible ten-question Friend Challenge and send it to someone else. Gameplay remains offline-first and distraction-free; the only ad placement is a banner on the Play home screen.

## Highlights

- Sixteen categories, including Mythology & Legends and Video Games
- 2,341 hand-authored questions with Easy, Medium, and Hard tiers
- **Quick Play:** one tap launches ten different categories on a 3 Easy / 4 Medium / 3 Hard ramp
- Ten-question category rounds with immediate feedback and short explanations
- A deterministic Daily Challenge shared by every player, with local streak tracking
- One-attempt Friend Challenges with tap-to-open `eztrivia://challenge/...` links plus short fallback codes
- Thirteen Game Center achievements plus six additional iCloud-backed EZ Trivia badges
- Native global and friends leaderboards inside the Scores tab, plus optional access to Apple's full Game Center dashboard
- Local recent-round and Quick Play history, category best scores, and difficulty-weighted lifetime points
- Automatic private player-state synchronization through iCloud key-value storage when iCloud is available
- In-game **Report this question** links that prefill the stable question ID and answer context in a support email
- Optional 2–15 second auto-advance, sound effects, and haptics in the Settings tab
- Shareable square result cards for category, Quick Play, Daily, and Friend Challenge rounds
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

`Scripts/analyze_questions.py` reports on the catalog without a Swift toolchain: length bias, giveaway answers, explanations that never name their answer, and prompts that restate a question already in the category. `Scripts/append_seeds.py` authors new questions against those same rules and refuses a seed that reuses a keyed answer the category already has. `Scripts/check_seed_syntax.py` parses the seed files as Swift array literals and catches a malformed splice in under a second rather than minutes into a CI build.

The local catalog contains all 249 ISO 3166-1 flag assets. Five are not asked because their artwork is identical to another valid answer, and three more are excluded because the bundled design is contested or no longer current. The remaining 241 flags are askable. Near-identical flags at phone size are never offered against one another, and flag distractors are redrawn from the same difficulty pool each time.

Answer order is shuffled at presentation time. Daily Challenges use seeded selection, while Friend Challenges use a repository-owned seeded algorithm so another device receives the same questions and option order. Version-3 Friend Challenge codes use an explicit sixteen-category roster and carry a checksum, allowing future releases to reject incompatible or mistyped codes instead of silently producing a different round. Friend Challenge links embed that same code in the `eztrivia` URL scheme; the code remains visible in shared text as a fallback.

Quick Play is intentionally non-deterministic: it chooses ten different categories and follows the same 3 Easy / 4 Medium / 3 Hard progression as the Daily and Friend Challenge, preferring questions the player has not recently seen.

The correct answer is not identifiable simply by selecting the longest option. Tests cap the heuristic at 35% in every text pool and separately budget conspicuous per-question length gaps. Additional editorial checks limit repeated keyed answers, measure whether explanations reinforce their answers, enforce concise U.S.-English copy for newly authored categories, and fail when a scheduled rule or record verification becomes overdue.

## Run core tests

The question, scoring, Daily Challenge, Friend Challenge, Quick Play selection, deep-link parsing, and share-text logic are exposed as a Swift package:

```bash
swift test
```

CI also builds and archives the SwiftUI app because package tests do not compile the iOS interface, iCloud integration, URL scheme, or GameKit integration.

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

Every revealed explanation includes **Report this question**. It opens a prefilled email containing the stable question ID, prompt, selected and correct answers, explanation, difficulty, and app version; nothing is transmitted unless the player sends the email.

## Player-state persistence and iCloud

`ScoreStore` remains offline-first: gameplay state is persisted locally in `UserDefaults`. When iCloud key-value storage is available, a compact player-state snapshot is also synchronized across devices on the same iCloud account. It includes category history, seen-question state, Daily and Friend Challenge records, Quick Play history, lifetime points, and achievement facts.

Category lifetime points migrate into a shared baseline and then record per-installation increments. iCloud merges those increments by installation before rebuilding the total, so new play on two devices adds together instead of simply taking the larger device total. One-attempt Daily and Friend Challenge records keep the earliest completion for a duplicated day/seed. Recent-history and seen-question reset timestamps prevent a cleared local history from being resurrected by an older cloud snapshot.

The target declares `com.apple.developer.ubiquity-kvstore-identifier`. The production App ID must have iCloud Key-Value Storage enabled before a signed build using this entitlement is distributed; see `RELEASE_SETUP.md`.

## Production services

### AdMob banner

The banner implementation and identifiers are already present in source:

- Debug app ID and banner ID use Google's official test values.
- Release uses production app ID `ca-app-pub-3388571830343061~4987263013` and banner ID `ca-app-pub-3388571830343061/4408678515`.
- `AdConsentManager` completes the User Messaging Platform flow before `AdBannerView` requests an ad.
- The banner is pinned above the tab bar on the Play home screen and disappears during gameplay.

There is no additional code switch to turn it on. Live display requires the matching iOS app and banner unit to be active in AdMob, the applicable privacy message to be published, and a Release/TestFlight build with ad inventory available. See `RELEASE_SETUP.md` for the account and device checklist.

### Game Center and achievements

The bundle identifier is `com.rsm.eztrivia`, and the target contains the Game Center entitlement. Category leaderboards rank monotonic lifetime points; the Daily leaderboard ranks the day's weighted score. `Scripts/configure_game_center.py` idempotently provisions the seventeen leaderboards during an authenticated TestFlight archive.

The original thirteen Game Center achievement definitions remain unchanged and consume Apple's full 1,000-point per-app achievement budget. EZ Trivia therefore adds six newer progression badges as in-app/iCloud achievements rather than changing live Game Center IDs or exceeding Apple's point limit. The Game Center definitions are mirrored in `EZTriviaApp/Achievements.swift` and `RELEASE_SETUP.md`. Required 1024×1024 artwork for the Game Center achievements is checked into `Artwork/Achievements`.

## Project layout

- `EZTriviaApp/` — SwiftUI views, settings, local/iCloud persistence, AdMob, sharing, and GameKit integration
- `Sources/EZTriviaCore/` — reusable models, question bank, scoring, Daily Challenge, Friend Challenge, Quick Play selection, and share/deep-link logic
- `Sources/EZTriviaCore/Questions/` — hand-authored text questions, one file per category
- `Tests/EZTriviaCoreTests/` — deterministic engine, sharing/deep-link, and content-quality tests
- `Artwork/Achievements/` — production-sized Game Center achievement artwork
- `QuestionReview.csv` — spreadsheet-friendly content review export
- `RELEASE_SETUP.md` — cloud build, App Store Connect, Game Center, AdMob, iCloud, and TestFlight setup

## Privacy

The public policy is in `PRIVACY.md`. `PrivacyInfo.xcprivacy` declares the app's first-party data categories. Review final advertising consent behavior, iCloud disclosures, and App Store privacy answers with qualified counsel before release.
