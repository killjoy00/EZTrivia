# Android Scores / Settings parity, referenced to iOS 1.4

Android v8 is the deployed baseline (owner confirmation November 21, 2026). This is a **new candidate**: it must not overwrite v8, restart any testing track, or deploy until review and verification.

## Scores: follow \`EZTriviaApp/LeaderboardView.swift\`

| Order | iOS | Android candidate |
| --- | --- | --- |
| Top | Game Center: Leaderboards, Achievements, Full Game Center/status | Google Play Games: Leaderboards, Achievements, Full Play Games/status |
| Next | Question coverage counter, progress bar and short explanation | Same; mention Saved Games instead of iCloud |
| Next | Empty state if no category or Quick Play rounds | Same |
| Next | Recent category rounds (first 10, expandable) | Same compact grouped rows |
| Next | Recent Quick Play (first 10) | Same compact grouped rows |
| Last | Lifetime points by category | Same compact grouped rows |

Daily and Friend history are *retained*, but are not separate sections in iOS's Scores screen. Android likewise omits these extra sections without deleting data. Clear removes only recent category rounds.

### In-app leaderboards

- Native Android Compose selector defaults to Daily Challenge then sixteen categories in iOS's category order.
- Global/Friends, your rank, top ten players and total ranked players, based only on real Play Games data.
- Friends permission is explicitly requested when Play Games requires it; a canceled/denied prompt does not fabricate results.
- Offline/signed-out, loading, empty, error and retry states are explicit.
- Full Play Games UI remains optional as a fallback; it is no longer the only leaderboard destination.

## Settings: follow \`EZTriviaApp/SettingsView.swift\`

- Gameplay: Auto-advance and Delay only while enabled.
- Daily Challenge: Streak reminders and Remind me at only while enabled; show notification permissions warning only when needed.
- Feedback: Sound effects and Haptics.
- Appearance: Android-only Dark mode toggle, without explanatory paragraphs.
- Google Play Games: concise connected/sync status and Sync now or Connect; standings and achievements belong in Scores, not Settings.
- Remove Ads: purchase with localized price, Restore purchases, Try again if product cannot be fetched. Privacy choice appears only when required by Google.
- Support: Send feedback and Report a problem; diagnostic details go in the email draft, *not* on the screen.
- Remove the noninteractive Accessibility and Progress essays; no accessibility functionality or persisted state should change.

## Google Play Billing investigation and release requirements

- Historically, the app's Play API audit reported \`com.rsm.eztrivia.removeads\` ACTIVE at US $0.99, but that is **not evidence** that Android v8's exact installed build and test Google account can complete a purchase today.
- On opening Settings, retry failed product lookups without requiring a restart; show loading vs unavailable accurately.
- Query the current eligible one-time offer **immediately before checkout** rather than attempting a stale cached ProductDetails object.
- Always include the eligible offer token in BillingFlowParams.
- Handle already-owned as restore; distinguish an unfetched product and \`NO_ELIGIBLE_OFFER\` from generic network failures.
- Persist entitlement only after a purchased transaction or authoritative owned-purchases refresh. Keep acknowledgement and banner suppression.
- No bypass/pretend purchase and no Play Console product mutation from this PR.
- Manual Play-installed test required: eligible tester login, product price shown, cancel, buy (using Play license-test payment), pending, relaunch, restore after clearing app state, banner disappears, refund/revocation, and Report a problem captures a useful Billing code. A native source build or unit test cannot prove Play purchase availability.

## Automated coverage / acceptance

- [ ] Android CI: Kotlin unit tests, lint, debug/release compile, R8/AAB checks.
- [ ] UI: Scores first section and in-app leaderboard selector available without Play Games sign-in; Daily first and 16 categories.
- [ ] UI: Compact grouped recent category / Quick Play rows and lifetime totals; no duplicated Daily/Friend history sections.
- [ ] UI: Settings short sections, no redundant essays; current preferences and notification permission flows still work.
- [ ] Billing: query/checkout/restore state errors are visible, retryable and preserve ownership on temporary API failures.
- [ ] Installed Android: Google Play Games standings behave for signed-in/friends/global/test players; Saved Games unaffected.
- [ ] Installed Android v9+ candidate: verify actual purchase and entitlement on a Play-install and license test account. Do not claim the IAP works until proven.
