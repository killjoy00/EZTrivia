# EZ Trivia release setup

> **Do this first: the bundle identifier is not valid yet.**
>
> The target currently sets `PRODUCT_BUNDLE_IDENTIFIER = EZTrivia`. App Store
> Connect requires reverse-DNS form, so `EZTrivia` cannot be registered as-is.
> Pick a real identifier before starting any step below — every occurrence of
> `EZTrivia` in this document then means *your* identifier. It cannot be changed
> after the App Store and Firebase records are created, so choose once.
>
> Suggested: `com.mindell.eztrivia`
>
> Update it in `EZTrivia.xcodeproj/project.pbxproj` (both the Debug and Release
> configurations) and keep it identical in App Store Connect, the AdMob app
> record, and the Firebase iOS app record.

This checklist covers the remaining account-side work for the first iOS release in the United States and Canada. The Xcode target is already configured with bundle identifier `EZTrivia`, Apple Team ID `3564X3VTDB`, display name **EZ Trivia**, a 4+ product intent, non-personalized AdMob requests, and the supplied production AdMob identifiers.

> Never paste an Apple `.p8` private key into an issue, PR, source file, or ordinary chat message. Firebase's `GoogleService-Info.plist`, AdMob app ID, and ad-unit ID are app configuration rather than private keys.

## 1. Confirm the Apple App ID and enable Game Center

1. Sign in to [Apple Developer — Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list).
2. Select **Identifiers** in the left sidebar.
3. Search for and open the explicit identifier whose Bundle ID is exactly `EZTrivia`.
4. Confirm its Team/App ID Prefix is `3564X3VTDB`.
5. Under **Capabilities**, enable **Game Center**.
6. Select **Save**, then confirm the change.
7. If `EZTrivia` does not exist, select the **+** button, choose **App IDs → App**, select **Explicit**, enter `EZTrivia`, enable Game Center, and register it. Do not create a second identifier if the explicit identifier already exists.

The repository already contains the Game Center entitlement. Xcode automatic signing will regenerate the development/distribution profiles after the capability is enabled in the developer portal.

## 2. Create or confirm the App Store Connect app

1. Sign in to [App Store Connect](https://appstoreconnect.apple.com/).
2. Open **Apps**.
3. If the app does not exist, select **+ → New App**.
4. Set **Platforms** to **iOS**.
5. Set **Name** to `EZ Trivia`.
6. Set **Primary Language** to **English (U.S.)**. This is the single required storefront language; no translated localizations are needed.
7. Select the Bundle ID for `EZTrivia`.
8. Enter an internal SKU such as `EZTRIVIA-IOS-001`.
9. Leave user access at **Full Access** unless the account has a specific access policy.
10. Create the app record.

If the record already exists, open **App Information** and confirm the name and bundle ID rather than creating another record.

## 3. Enable and configure Game Center

1. In App Store Connect, open **EZ Trivia**.
2. Open the app's **Game Center** section. Depending on the current App Store Connect layout, this can appear under the app's Services or Features area.
3. Enable Game Center for the app if an enable/setup button is shown.
4. Create eleven **Classic Leaderboards**—not a leaderboard set or recurring leaderboard.
5. For every leaderboard, choose:
   - **Sort order:** High to Low
   - **Score range:** 0 through 100
   - **Score format:** Integer
   - **Submission type:** Best score
6. On Apple's first leaderboard form:
   - **Reference Name** is an internal App Store Connect label. Use the value in the first column below. Players do not see it.
   - **Leaderboard ID** is the permanent identifier used by the app. Copy the second-column value exactly, including capitalization and periods. It cannot be changed after creation.

| Reference Name (internal) | Leaderboard ID (exact) | English (U.S.) display name shown to players |
| --- | --- | --- |
| Football High Scores | `EZTrivia.football` | Football High Scores |
| Basketball High Scores | `EZTrivia.basketball` | Basketball High Scores |
| Soccer High Scores | `EZTrivia.soccer` | Soccer High Scores |
| World Flags High Scores | `EZTrivia.flags` | World Flags High Scores |
| History High Scores | `EZTrivia.history` | History High Scores |
| Science High Scores | `EZTrivia.science` | Science High Scores |
| Movies High Scores | `EZTrivia.movies` | Movies High Scores |
| Geography High Scores | `EZTrivia.geography` | Geography High Scores |
| Music High Scores | `EZTrivia.music` | Music High Scores |
| Animals High Scores | `EZTrivia.animals` | Animals High Scores |
| Food & Drink High Scores | `EZTrivia.food` | Food & Drink High Scores |

7. After creating each leaderboard, add its one required **English (U.S.)** localization and enter the third-column display name. “No localization” means no translations; Apple still requires this one default player-facing language.
8. Save every leaderboard.
9. Return to the app version page and associate the Game Center leaderboards with the version if App Store Connect presents that option.

## 4. Limit storefront availability to the United States and Canada

1. Open **Pricing and Availability** for EZ Trivia in App Store Connect.
2. Find **App Availability** or **Countries or Regions**.
3. Choose the option to manage availability manually.
4. Deselect all storefronts.
5. Select only **Canada** and **United States**.
6. Save the change.

This setting is controlled by App Store Connect and cannot be enforced by application source code.

## 5. Complete the 4+ age rating

1. Open **App Information → Age Ratings** in App Store Connect.
2. Start or edit the age-rating questionnaire.
3. Answer each content question based on the shipped app. The current app contains general trivia, a score leaderboard, and banner advertising; it does not contain chat, user-generated content, gambling, loot boxes, unrestricted web access, graphic violence, sexual content, or controlled-substance content.
4. Disclose advertising when the questionnaire asks about ads or related capabilities.
5. Confirm the calculated result is **4+** before saving. If Apple calculates a higher rating, do not override factual answers—review the triggering answer instead.
6. Revisit the questionnaire whenever content, advertising, or external links change.

## 6. Finish AdMob for non-personalized banner ads

The Release build already uses:

- App ID: `ca-app-pub-3388571830343061~4987263013`
- Banner ad-unit ID: `ca-app-pub-3388571830343061/4408678515`

Debug builds deliberately use Google's official test IDs so development cannot generate invalid live-ad traffic.

1. Sign in to [Google AdMob](https://admob.google.com/).
2. Open the app matching the production App ID above.
3. Confirm its platform is iOS and its bundle ID is `EZTrivia`.
4. Confirm the banner unit above is active.
5. Open **Privacy & messaging** from AdMob's left navigation. If the page opens on an overview, select the **Messages** tab.
6. Find the **US state regulations** card—not the European regulations/GDPR card—and select **Create message**. If a draft already exists, open the draft instead.
7. Select **EZ Trivia** under **Select apps**, then select **Confirm**. An unpublished app can still be selected if it is already registered in AdMob.
8. Enter an internal message name such as `EZ Trivia US privacy choices`.
9. Keep **English** as the only language. No French translation is planned for this release.
10. In the message editor, keep the required “Do not sell or share my personal information”/privacy-choice controls enabled. Do not add a personalized-ad upsell.
11. Review the styling and privacy-policy URL, then select **Publish**. If Google asks whether to use its default consent-management setup, choose the Google-certified/default option used by the existing UMP integration.
12. Canada does not currently use the US-state message. Because the app always sends `npa=1`, Canadian requests are also non-personalized. If AdMob later presents a Canada-specific regulatory message for this account, publish it using the same English-only, non-personalized approach.
13. Return to **Privacy & messaging** and confirm the US-state message status is **Published**, not Draft.
14. In **Blocking controls**, select ad categories appropriate for a 4+ general-audience trivia app. Block sensitive categories and review the ad-content rating.
15. Do not tap live ads during development or testing. Use Debug builds for test ads.

The application also adds `npa=1` to every banner request, enforcing non-personalized ad requests in addition to the consent flow.

## 7. Optional: Firebase Analytics and Crashlytics

Firebase is **not required** to publish or run EZ Trivia. Without `GoogleService-Info.plist`, the current integration deliberately remains disabled and sends no Firebase events. App Store Connect still provides basic App Analytics, and Apple/Xcode provides opt-in crash reports and Organizer diagnostics.

For the simplest, most privacy-minimal first release, skip Firebase. Use Firebase only if you specifically want its cross-version event dashboards and near-real-time Crashlytics console. The SDK integration is already available; enabling it only requires the app-specific configuration file.

1. Sign in to the [Firebase console](https://console.firebase.google.com/) with the Google account that should own the app data.
2. Select **Create a project**.
3. Name it `EZ Trivia` (the internal Firebase project ID may be different and globally unique).
4. Enable Google Analytics when prompted.
5. Select or create the Analytics account owned by the same business/account, then finish creating the project.
6. On the project overview, select **Add app → iOS**.
7. Enter the Apple bundle ID exactly as `EZTrivia`. Bundle IDs are case-sensitive and cannot be changed for that Firebase app later.
8. Enter `EZ Trivia` as the optional app nickname. The App Store ID can be left blank until Apple assigns one.
9. Register the app.
10. Download `GoogleService-Info.plist`.
11. Provide that file for inclusion in the repository/app target. It is configuration, not a service-account private key, but it must match this exact Firebase project and `EZTrivia` bundle ID.
12. In the Firebase console, open **Build → Crashlytics** and select **Enable/Get started** if prompted.
13. Open **Analytics → Dashboard** once the first test build has run to confirm events arrive.
14. Open **Crashlytics** after a test crash or nonfatal report to confirm symbolicated reports arrive.

Do not create or send a Firebase Admin SDK JSON/service-account key. The iOS app only needs `GoogleService-Info.plist`.

## 8. App privacy and release metadata

1. Host `PRIVACY.md` at a public HTTPS URL. The GitHub file URL can be used initially, though a stable product website is preferable.
2. In App Store Connect, enter that URL in **App Privacy → Privacy Policy URL**.
3. Complete App Privacy answers for Google Mobile Ads, Firebase Analytics, Firebase Crashlytics, and Game Center based on the final enabled settings and Google's current SDK disclosures.
4. State that ads are requested as non-personalized and that gameplay interaction/crash diagnostics are used for analytics and app functionality.
5. Add support and marketing URLs, copyright holder, category (**Games → Trivia**), description, keywords, screenshots, and review notes.
6. In review notes, explain that Game Center is optional and the home-screen banner uses non-personalized AdMob requests.

## 9. Final device and submission checks

1. Open the project in the latest production Xcode on macOS and let Swift packages resolve.
2. Confirm the selected signing team is `3564X3VTDB` and automatic signing reports no errors.
3. Add the downloaded `GoogleService-Info.plist` to the `EZTrivia` target with **Copy items if needed** enabled.
4. Run a Debug build on a physical iPhone. Confirm the banner is Google's test banner, not the live ad unit.
5. Sign in with a Game Center sandbox tester, complete rounds in multiple categories, and verify scores appear in Apple's dashboard.
6. Verify local flag questions with Airplane Mode enabled.
7. Test VoiceOver, Dynamic Type, dark mode, consent/privacy options, local score deletion, and interrupted rounds.
8. Archive a Release build and validate it in Xcode Organizer.
9. Upload to App Store Connect, distribute to internal TestFlight testers, and test the Release configuration without tapping ads.
10. Complete export compliance, content rights, age rating, privacy, availability, and review-contact fields, then submit the selected build for review.

## App Store Connect API key security and optional automation

Any App Store Connect `.p8` private key pasted into chat, email, an issue, or a PR must be treated as compromised and revoked immediately in **App Store Connect → Users and Access → Integrations → App Store Connect API**. Do not reuse that key.

Most Game Center and app-record work can be automated with a fresh App Store Connect API key, but account-owner actions and some review questionnaires may still require the web interface. If automation is desired later, create a new dedicated key with the minimum **App Manager** access and transfer the **Issuer ID**, **Key ID**, and `.p8` file through a secure secret/file-transfer mechanism. Never commit the `.p8` file or paste its contents into chat.
