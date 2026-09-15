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

The automated Play-image workflow now generates and uploads one 1024 × 500 feature graphic plus five 1080 × 1920 en-US phone screenshots, then verifies those images in a fresh Play edit.

The Play Store basics workflow generates a deterministic 512 × 512 opaque RGB store icon from the shipped 1024 × 1024 app icon and publishes it together with the developer contact website/email. It independently re-reads Play after commit to verify all three values.

Current phone screenshot sequence:

1. **Play** — top-level Play screen showing Quick Play plus the category/difficulty entry points.
2. **Question + explanation** — a real question after answering, with the explanation visible.
3. **Daily Challenge** — Daily round or result/streak state.
4. **Friend Challenge** — create/join flow demonstrating the shared-link/code feature.
5. **Scores** — question coverage, recent results, and lifetime points.

Use actual in-game UI. Do not put features in screenshots that are not present in the Android build. Keep the first three images focused on gameplay rather than Settings or purchase UI.

Current Google screenshot/feature-graphic requirements:
https://support.google.com/googleplay/android-developer/answer/9866151

## Live Play release audit

The reusable manual `Google Play release audit` workflow uses the existing Android Publisher service account and creates a disposable edit snapshot to inspect live Play state without committing release changes. It archives a non-secret JSON report for release evidence.

The September 15, 2026 audit established:

- accepted bundle count: **1**;
- highest accepted `versionCode`: **2**;
- Internal track: one completed release containing **versionCode 2**;
- Production track: **0 releases**;
- en-US listing: one locale, title `EZ Trivia`, expected short description, and a populated full description;
- developer contact website: `https://killjoy00.github.io/EZTrivia/support.html`;
- developer contact email: `killjoy00@yahoo.com`;
- Play assets: **1 store icon, 1 feature graphic, 5 phone screenshots**;
- Remove Ads: **ACTIVE**, US price **$0.99**, available in **173 regions** in the API audit;
- public privacy policy, support page, `app-ads.txt`, and Digital Asset Links all returned HTTP **200**;
- Play's tester endpoint reported zero Google Groups, which does **not** prove there are no testers because that API does not expose Play Console email-list membership.

The country-availability endpoints were not reliable enough to treat as authoritative in this audit: Internal returned HTTP 400 and Production returned HTTP 204. Confirm final Production country/device availability in Play Console before launch rather than inferring it from those responses.

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

## Data safety — submitted

The Google Play Data safety declaration was submitted programmatically through the Android Publisher v3 `applications.dataSafety` endpoint and accepted with HTTP 204 in workflow run `34893523302` on September 14, 2026 UTC. The declaration is generated from Google's CSV format and archived as a workflow artifact for release evidence.

The submitted global/account answers are:

- User data is collected/shared: **Yes**.
- All declared collected data is encrypted in transit: **Yes**.
- EZ Trivia account creation: **None**. The app does not operate a developer-run account system.
- Outside-app account login into an EZ Trivia account: **No**. Optional Google Play Games authentication is a Google platform profile, not an EZ Trivia account.
- Developer-provided global data-deletion request mechanism: **No**. Local data and Google-managed data have separate controls; EZ Trivia does not operate one deletion endpoint covering all bundled SDK data.

The declaration includes these nine data types:

| Play Data safety type | Collected | Shared | Required / optional | Main reasons |
| --- | --- | --- | --- | --- |
| Approximate location | Yes | Yes | Required for ad-supported path | App functionality, analytics, fraud/security, advertising |
| Page views and taps in app | Yes | Yes | Required for ad-supported path | Analytics, fraud/security, advertising |
| Diagnostics | Yes | Yes | Required for ad-supported path | App functionality, analytics, fraud/security, advertising |
| Device or other identifiers | Yes | Yes | Required for ad-supported path | App functionality, analytics, fraud/security, advertising |
| Name | Yes | Yes | Optional | Play Games functionality |
| Email address | Yes | Yes | Optional | Play Games functionality |
| Personal identifiers | Yes | Yes | Optional | Play Games functionality plus applicable Google SDK purposes |
| Other actions / gameplay activity | Yes | Yes | Optional | Play Games/Saved Games functionality, analytics, fraud/security |
| Purchase history | Yes | No | Optional | Remove Ads purchase functionality |

EZ Trivia does **not** declare payment-card/bank-account data, precise location, files/docs, messages, photos/videos, audio, contacts, calendar, or a first-party crash-log category. The app has no first-party analytics or crash-reporting SDK.

Google Mobile Ads SDK 25.4.0 is the reason the declaration includes approximate location, app interactions, diagnostics, and identifiers in the required ad-supported path. UMP gates ad requests where applicable, and the app stops requesting the banner after Remove Ads is owned. Optional Play Games features account for the Play Games identity and gameplay categories. Google Play Billing provides purchase ownership to the app but EZ Trivia does not receive payment credentials or operate a purchase backend.

The exact declaration, rationale, automation behavior, and future-review rules are recorded in `android/DATA_SAFETY.md`.

References:

- https://support.google.com/googleplay/android-developer/answer/10787469
- https://developers.google.com/admob/android/privacy/play-data-disclosure
- https://developers.google.com/android-publisher/api-ref/rest/v3/applications.dataSafety

## Release/compliance checklist

Before moving beyond Internal Testing:

- [ ] Create the Android EZ Trivia app in AdMob for package `com.rsm.eztrivia`.
- [ ] Create a production banner ad unit and configure AdMob Privacy & messaging.
- [ ] Add repository secrets `ANDROID_ADMOB_APP_ID` and `ANDROID_ADMOB_BANNER_ID`.
- [x] Create and activate Google Play one-time product `com.rsm.eztrivia.removeads` with its default purchase option and price.
- [ ] Enable **Saved Games** for the linked Google Play Games Services project.
- [ ] Install from a Play testing track on two Android devices using the same Play Games profile; make independent progress offline on both, reconnect, and verify histories, lifetime points, Daily/Friend one-attempt results, question progress, and round counters merge without duplication or loss.
- [ ] From a Play testing build, validate consent, banner display, purchase, pending purchase, restore, and entitlement revocation/refund behavior.
- [x] Publish the en-US Main store listing copy, developer contact website/email, 512 × 512 store icon, feature graphic, and real Android phone screenshots.
- [ ] Complete Ads, App access, Target audience, and Content rating forms.
- [x] Submit the Data safety declaration through the Android Publisher API and archive the accepted CSV.
- [x] Confirm the published privacy-policy and support URLs load publicly after the Data Safety-aligned privacy update.
- [ ] Publish/finish testing Google Play Games Services resources when runtime validation is complete.
- [x] Publish and verify root-domain Digital Asset Links for Friend Challenge links.
- [ ] Confirm final Production countries/regions and device availability in Play Console; the current Android Publisher country-availability endpoints are not reliable enough to use as proof.
- [ ] Run the signed Internal Testing release workflow with production AdMob secrets before any production promotion.

The project already targets API 36, which satisfies the Google Play target-API requirement in effect for new apps/updates beginning August 31, 2026.
