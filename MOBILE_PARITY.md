# EZ Trivia iOS / Android parity audit

**Audited:** November 20, 2026
**Source snapshot:** `main` @ `78378394ae2481f4c39020e4e869a586d9018bb7`
**Scope:** repository implementation and documented third-party configuration. Store-installed versions, production entitlements, actual devices, and release approvals are *not* inferred from source.

## Comparison before the iOS category-context fix

| Surface | Result | Evidence and qualification |
| --- | --- | --- |
| Category roster | Parity | Both model files define the same 16 wire names, titles, and subtitles; iOS home loops through `TriviaCategory.allCases`, Android through `TriviaCategory.entries`. |
| Question content | Parity by design | iOS authors the question catalog; Android's generated asset is exported from the Swift bank. README describes 2,341 questions. Validate the generated catalog in CI. |
| Category rounds | Parity in core | Both have three difficulty tiers and a ten-question round with scoring, explanations and history. |
| Quick Play | Parity in core, UI gap | Ten distinct categories and a 3 Easy / 4 Medium / 3 Hard progression. Android shows each question's category, iOS does not. |
| Daily Challenge | Parity from Daily #252 onward, UI gap | Both use the frozen v2 seeded algorithm from September 9, 2026. Earlier iOS rounds intentionally retain the legacy algorithm. Android shows category per question; iOS does not. |
| Friend Challenge | Parity in v3 algorithm, UI gap | Shared 16-category roster, code version 3, and deterministic golden-vector coverage. Android displays category per question; iOS does not. |
| Category label in category-only round | Parity | Category/difficulty already appear in the top header. Do not add a second label in this mode. |
| Score history and progress | Feature parity with platform-dependent persistence | Both keep local category/Quick Play/Daily/Friend history and question coverage. iOS syncs through iCloud; Android implements Google Play Games Saved Games. These are not cross-platform cloud accounts. |
| Achievements | 19 in each client, different provider behavior | iOS: 13 Game Center achievements + 6 private in-app/iCloud badges. Android: 19 Play Games achievement IDs. Platform metadata/publication and user account state require live verification. |
| Leaderboards | Platform equivalents | iOS uses Game Center global/friends leaderboards; Android uses Google Play Games. Both have category plus Daily IDs in code; equivalence of live publication is unverified. |
| Sound, haptics, auto-advance, streak reminders | Broad parity | Both offer these controls with matching delay (2–15 seconds) and reminder hour (12–22) policies. |
| Dark appearance | UX difference | iOS follows the OS appearance; Android has its own persistent Dark mode toggle (default light), so a user cannot select it the same way on both. |
| Accessibility auto-advance | iOS gap | Android suppresses auto-advance during TalkBack touch exploration; iOS auto-advance currently does not check VoiceOver, potentially cutting an explanation short. |
| Question reporting | Android gap | iOS offers a report link inside each revealed question. Android offers general feedback/reporting from Settings but not the question-specific link in the explanation card. |
| Ads and Remove Ads | Feature parity by design | Both have home-screen banner ads, consent, and a one-time Remove Ads purchase. Live ads, inventory and store-product availability not checked. |
| Store/deployment | Not established | A current `main` commit is not proof of an installed App Store / Play release. Store build/version, review state, and production audience need an authenticated or public listing check. |

## Specific follow-ups

1. **This change (P0):** show a compact category label in iOS Quick Play, Daily Challenge, and Friend Challenge, and not in category-only rounds. Guard the rule with tests.
2. **P1 accessibility:** make iOS auto-advance pause while VoiceOver is speaking/exploration is enabled, like Android, with an explicit regression test.
3. **P1 question feedback:** consider adding the existing iOS per-question report affordance to Android, including the question ID and selected answer.
4. **P1 Play Games release audit:** Android `android/PLAY_GAMES.md` records a prior live check where 19 achievements and 17 leaderboards were *draft-only*. This is historic evidence, not a current-state claim. Re-run the Play Games audit, verify Saved Games toggle and two-device sync, and publish configuration if needed.
5. **P2 appearance:** decide whether iOS should offer a manual light/dark override, or Android should follow the OS setting; both already render in dark mode.
6. **Before paired store releases:** identify exact live versions and validate representative installed-device flows. Avoid saying the two store releases are at parity just because `main` is.

## Category-label acceptance matrix

| iOS path | Expected on every question |
| --- | --- |
| Quick Play | Category chip immediately above question/flag |
| Daily Challenge | Category chip immediately above question/flag |
| Friend Challenge | Category chip immediately above question/flag |
| Single-category round | No redundant chip; header retains category and difficulty |

Automated gate: Swift unit test for all four explicit round kinds, the existing core package suite, Xcode iOS build/archive, and Android catalog/CI tests. Visual QA still requires an actual iOS build or simulator capture, including small-screen / large-text and flag questions.

## Evidence

- iOS categories: `Sources/EZTriviaCore/Models.swift`; Android categories: `android/app/src/main/java/com/rsm/eztrivia/model/TriviaModels.kt`.
- Shared iOS player: `EZTriviaApp/RoundPlayer.swift`; callers: `EZTriviaApp/GameView.swift`, `EZTriviaApp/DailyChallengeView.swift`, `EZTriviaApp/FriendChallengeView.swift`.
- Android category chip and call sites: `android/app/src/main/java/com/rsm/eztrivia/ui/QuestionCategoryLabel.kt`, `EZTriviaApp.kt`, `DailyChallengeScreen.kt`, `FriendChallengeScreen.kt`; original Android implementation: PR #94.
- iOS settings/achievements/progress: `EZTriviaApp/SettingsView.swift`, `Achievements.swift`, `ScoreStore.swift`; Android equivalents: `android/app/src/main/java/com/rsm/eztrivia/ui/SettingsScreen.kt`, `model/Achievements.kt`, `data/PlayerStateStore.kt`.
- Release integration: `.github/workflows/ci.yml`, `.github/workflows/testflight.yml`, `RELEASE_SETUP.md`, `android/PLAY_GAMES.md`, and Android release docs.
