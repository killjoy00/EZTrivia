# Google Play store readiness

Prepared for the Android package `com.rsm.eztrivia`. Re-check the linked Google Play guidance before production submission because policy wording and console fields can change.

## Main store listing

**App name**

    EZ Trivia

**Category**

    Game → Trivia

**Short description** — 70 characters, below Google Play's 80-character limit

    2,341 questions, Daily Challenge, Quick Play, and head-to-head trivia.

**Full description**

EZ Trivia makes it easy to play a quick round, test yourself in a favorite category, or settle a score with a friend.

Choose from 16 categories and three difficulty levels, or jump into Quick Play for a ten-question mix that ramps from Easy to Hard. Every answer includes a short explanation, so even a miss can teach you something.

Come back each day for the Daily Challenge and play the same ten-question set for that day. Build a streak, compare your score, and track your history.

Want a head-to-head round? Create a Friend Challenge and share the link or short code. Your friend gets the exact same questions and answer order.

Track question coverage, recent results, lifetime category points, and achievements. When Google Play Games is connected and Saved Games is available, EZ Trivia can synchronize private gameplay progress across your Android devices while also mirroring configured achievements and leaderboard scores. Core trivia play does not require a separate EZ Trivia account or a network connection.

EZ Trivia includes 2,341 questions across 16 categories, including history, science, sports, geography, entertainment, flags, and more.

Advertising is limited to a banner on the Play screen. A one-time Remove Ads purchase is available through Google Play.

This description assumes Google Play Games Saved Games is enabled and validated before production. If Saved Games is not enabled for the production PGS project, remove the cross-device progress-sync sentence before publishing the listing.

**Privacy policy**

    https://killjoy00.github.io/EZTrivia/privacy.html

**Support / website**

    https://killjoy00.github.io/EZTrivia/support.html

Google's current listing limits are 30 characters for the app name, 80 for the short description, and 4,000 for the full description:
https://support.google.com/googleplay/android-developer/answer/9859152

## Graphics and screenshots

Required assets:

- Play icon: 512 × 512 PNG, following Play icon requirements.
- Feature graphic: 1024 × 500 JPEG or 24-bit PNG with no alpha.
- At least two screenshots are required overall. For a game, target at least three portrait screenshots at 1080 × 1920 or higher so the listing is eligible for the richer recommendation formats.

Recommended portrait screenshot sequence:

1. **Play** — top-level Play screen showing Quick Play plus the category/difficulty entry points.
2. **Question + explanation** — a real question after answering, with the explanation visible.
3. **Daily Challenge** — Daily round or result/streak state.
4. **Friend Challenge** — create/join flow demonstrating the shared-link/code feature.
5. **Scores** — question coverage, recent results, and lifetime points.
6. **Achievements / Play Games** — only if captured from a real configured testing build.

Use actual in-game UI. Do not put features in screenshots that are not present in the Android build. Keep the first three images focused on gameplay rather than Settings or purchase UI.

Current Google screenshot/feature-graphic requirements:
https://support.google.com/googleplay/android-developer/answer/9866151

## App content declarations

### Ads

**Contains ads: Yes.**

The production build has a banner on the top-level Play screen when UMP allows an ad request and the player does not own Remove Ads. Do not mark the app ad-free merely because a user can buy the Remove Ads entitlement.

### App access

Core gameplay does not require a separate EZ Trivia login or reviewer credentials. Google Play Games is optional platform authentication for achievements, leaderboards, and Saved Games progress sync; it is not a gate on the game.

### Target audience

The product and privacy policy do not position EZ Trivia as a children's app. Before production submission, select only the age groups the product is actually intended to target and make sure the advertising configuration matches that choice. Do not select younger age groups just to broaden reach; doing so can trigger Families requirements.

### Content rating

Complete the IARC questionnaire from the actual question catalog and game behavior. Do not copy an assumed rating from iOS. Trivia content spans many general-knowledge topics, so answer the questionnaire from the content rather than from the visual style of the app.

### News / health / government / financial / dating categories

Not applicable to the product as currently implemented. EZ Trivia is a general-knowledge trivia game.

## Data safety working draft

This section is a **submission checklist, not a substitute for reviewing the live Data safety form**. Google says developers must account for data handled by third-party SDKs as well as first-party code:
https://support.google.com/googleplay/android-developer/answer/10787469

### Known first-party behavior

- Gameplay remains local-first on Android. The app stores gameplay progress, history, seen-question state, preferences, lifetime points, and achievement-related facts in local DataStore files.
- EZ Trivia does not operate a separate Android account/profile backend and does not receive a player's Google Account password.
- When Play Games authentication is available, the app submits configured achievement progress, category lifetime-point scores, and the current Daily Challenge weighted score to Google Play Games Services.
- When Google Play Games Saved Games is available, the app reads and writes a private saved-game snapshot through the player's Play Games profile. That snapshot includes gameplay history, completed/correct question-progress facts, seen-question state, lifetime category points, achievement-related progress, and merge bookkeeping used to preserve independent offline progress from multiple devices.
- The Saved Games snapshot is used for app functionality / player-progress synchronization, not for advertising or developer-run analytics. Gameplay can continue offline when the service is unavailable, with synchronization retried later.
- EZ Trivia ships no first-party analytics or crash-reporting SDK.
- Google Play Billing processes the optional one-time Remove Ads purchase. The app caches only the entitlement state locally; payment details are not sent to an EZ Trivia server.
- Question reporting opens the user's email client with a prefilled draft. Nothing is sent unless the user chooses to send the email.

### Google Mobile Ads SDK disclosures

The Android build currently uses Google Mobile Ads SDK 25.4.0. Google's current SDK disclosure says the SDK automatically collects and shares the following for advertising, analytics, and fraud-prevention purposes:

| Google SDK data | Likely Play Data safety area to review |
| --- | --- |
| IP address, which can estimate general location | Approximate location |
| User product interactions such as app launch/taps | App activity / app interactions |
| SDK/app performance diagnostics | App info and performance / diagnostics |
| Advertising ID, app set ID, and applicable account identifiers | Device or other IDs |

Google also states this SDK data is encrypted in transit. Use the live SDK disclosure when completing the form, especially if the SDK version changes:
https://developers.google.com/admob/android/privacy/play-data-disclosure

Because Google's Mobile Ads disclosure explicitly says these data are both **collected and shared**, the Data safety form cannot accurately say that EZ Trivia collects or shares no user data once production ads are enabled.

### Google Play Games / Billing review points

The app also uses Google Play Games Services and Google Play Billing. Before submitting Data safety, compare the live Play Console/SDK Index guidance with the actual integration and make sure the form covers platform processing of gamer identity/device data, achievements/scores, private Saved Games progress, and purchase-related data where Google requires disclosure. The app itself does not read the player's name/email, send purchase tokens to an EZ Trivia backend, or operate a developer-run player-state server.

### Account deletion

EZ Trivia does not create a developer-run user account. Player-owned local data can be removed by clearing app data/uninstalling; Play Games Saved Games, achievements/leaderboards, and purchase records are controlled through the relevant Google account/platform controls. If the Play Console asks about account deletion, answer based on the fact that there is no separate EZ Trivia account rather than treating optional Play Games authentication as an EZ Trivia account system.

## Release/compliance checklist

Before moving beyond Internal Testing:

- [ ] Create the Android EZ Trivia app in AdMob for package `com.rsm.eztrivia`.
- [ ] Create a production banner ad unit and configure AdMob Privacy & messaging.
- [ ] Add repository secrets `ANDROID_ADMOB_APP_ID` and `ANDROID_ADMOB_BANNER_ID`.
- [ ] Create and activate Google Play one-time product `com.rsm.eztrivia.removeads` with its default purchase option and price.
- [ ] Enable **Saved Games** for the linked Google Play Games Services project.
- [ ] Install from a Play testing track on two Android devices using the same Play Games profile; make independent progress offline on both, reconnect, and verify histories, lifetime points, Daily/Friend one-attempt results, question progress, and round counters merge without duplication or loss.
- [ ] From a Play testing build, validate consent, banner display, purchase, pending purchase, restore, and entitlement revocation/refund behavior.
- [ ] Complete Main store listing using the copy above.
- [ ] Upload feature graphic and real Android screenshots.
- [ ] Complete Ads, App access, Target audience, Content rating, and Data safety forms.
- [ ] Confirm the published privacy-policy URL loads publicly and matches the Data safety answers.
- [ ] Publish/finish testing Google Play Games Services resources when runtime validation is complete.
- [ ] Publish root-domain Digital Asset Links for verified Friend Challenge links.
- [ ] Run the signed Internal Testing release workflow with production AdMob secrets before any production promotion.

The project already targets API 36, which satisfies the Google Play target-API requirement in effect for new apps/updates beginning August 31, 2026.
