# EZ Trivia release setup

> **Bundle identifier: `com.rsm.eztrivia`**
>
> Registered in the Apple Developer portal under Team ID `3564X3VTDB` with the
> description "Trivia for everyone". The Xcode project matches. It cannot be
> changed once the App Store Connect record exists, so every step
> below assumes exactly this string.

## 0. Building without a Mac

There is no Mac in this project's toolchain, so every build runs on a GitHub
Actions macOS runner. Two workflows do the work:

- `.github/workflows/ci.yml` runs on every push and pull request. It runs the
  core package tests, builds the app for the simulator (this is the only thing
  that type-checks the SwiftUI layer), and fails if `QuestionReview.csv` has
  drifted from the question bank. No secrets needed.
- `.github/workflows/testflight.yml` is run by hand from the **Actions** tab. It
  archives, signs, exports an IPA, and uploads it to TestFlight.

### What you do once, about fifteen minutes

1. **App Store Connect API key.** App Store Connect -> **Users and Access** ->
   **Integrations** -> **App Store Connect API** -> **+**. Create a *team* key
   with the **App Manager** role. Download the `.p8`: it is downloadable
   **exactly once**. Note the Key ID and the Issuer ID shown above the list.
2. **Team ID** from developer.apple.com/account -> **Membership**. For this
   account it is `3564X3VTDB`.
3. **GitHub secrets.** Repository **Settings -> Secrets and variables ->
   Actions**, add four:

   | Secret | Value |
   | --- | --- |
   | `ASC_KEY_ID` | Key ID from step 1 |
   | `ASC_ISSUER_ID` | Issuer ID from step 1 |
   | `ASC_KEY_P8` | Full contents of the `.p8` file |
   | `APPLE_TEAM_ID` | `3564X3VTDB` |

   Paste the `.p8` straight into GitHub's secret field. Never commit it and
   never paste it into an issue, a pull request, or a chat message. The
   workflow normalises the common paste mistakes (Windows line endings,
   literal `\n`, base64, missing BEGIN/END armour) and validates that it parses
   before spending a build.
4. **Create the app record** in App Store Connect (section 2 below). This is
   not reliably automatable and takes two minutes.

No certificate or provisioning profile is ever exported by hand. The workflow
passes `-allowProvisioningUpdates` with the same API key, and Apple creates
what it needs on the runner.

### Running a build

1. **Actions** tab -> **TestFlight** -> **Run workflow**.
2. The build number comes from the GitHub run number automatically, so it always
   increases and Apple never sees a duplicate.
3. Roughly 20-40 minutes. The signed `.xcarchive` is saved as a run artifact,
   so a failed upload does not throw away a successful archive. With the export
   destination set to `upload`, Xcode may upload directly without leaving a
   local `.ipa`; the workflow's IPA artifact is therefore best-effort.
4. The workflow validates release-critical configuration in the archived app
   before uploading. App Store Connect performs its server-side validation as
   part of the authenticated export-and-upload operation.

### Diagnosing an immediate launch crash

The Google Mobile Ads SDK requires `GADApplicationIdentifier` in the installed
app's `Info.plist` and terminates during startup when it is absent. Do not rely
on custom `INFOPLIST_KEY_*` build settings for these identifiers: the iOS 26.2
archive produced by Xcode 26.3 omitted both custom keys even though the project
declared them. The app now uses an explicit `EZTriviaApp/Info.plist`; the macOS
CI build and archive are the definitive checks that Xcode accepts and packages
that configuration.

### What CI can and cannot prove

CI proves the app compiles, archives, exports, validates against Apple's
server-side checks, and uploads. It **cannot** prove a banner renders, a flag
image looks right, a layout fits, or a gesture feels correct. Those need a real
device with a TestFlight build.

## 1. Confirm the Apple App ID and enable Game Center

1. Sign in to [Apple Developer — Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list).
2. Select **Identifiers** in the left sidebar.
3. Search for and open the explicit identifier whose Bundle ID is exactly `com.rsm.eztrivia`.
4. Confirm its Team/App ID Prefix is `3564X3VTDB`.
5. Under **Capabilities**, enable **Game Center**.
6. Select **Save**, then confirm the change.

The repository already contains the Game Center entitlement. Xcode automatic signing will regenerate the development/distribution profiles after the capability is enabled in the developer portal.

## 2. Create or confirm the App Store Connect app

1. Sign in to [App Store Connect](https://appstoreconnect.apple.com/).
2. Open **Apps**.
3. If the app does not exist, select **+ → New App**.
4. Set **Platforms** to **iOS**.
5. Set **Name** to `EZ Trivia`.
6. Set **Primary Language** to **English (U.S.)**. This is the single required storefront language; no translated localizations are needed.
7. Select the Bundle ID for `com.rsm.eztrivia`.
8. The SKU is `EZTrivia`. It is an internal App Store Connect identifier only:
   never shown to users, unrelated to the bundle ID, and fixed once the app
   record exists.
9. Leave user access at **Full Access** unless the account has a specific access policy.
10. Create the app record.

If the record already exists, open **App Information** and confirm the name and bundle ID rather than creating another record.

## 3. Enable and configure Game Center

The repository includes `Scripts/configure_game_center.py`, an idempotent App
Store Connect API client that creates the eleven classic leaderboards below,
adds their English (U.S.) localizations, and leaves existing records unchanged.
It expects `ASC_KEY_ID`, `ASC_ISSUER_ID`, and `ASC_KEY_PATH` in its environment.
The key must have App Manager access. It intentionally stops with a readable
message if the app has no Game Center detail yet, because enabling Game Center
for the bundle identifier is a Developer portal operation rather than a
leaderboard API operation.

Authenticated TestFlight archives run this client automatically from an Xcode
build phase. Ordinary local and unsigned CI builds have no App Store Connect
environment variables, so the phase prints a skip message and performs no
network request. This keeps the provisioning operation repeatable without
placing an API key in the repository or requiring a Mac.

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
3. Confirm its platform is iOS and its bundle ID is `com.rsm.eztrivia`.
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

## 7. App privacy and release metadata

1. Host `PRIVACY.md` at a public HTTPS URL. The GitHub file URL can be used initially, though a stable product website is preferable.
2. In App Store Connect, enter that URL in **App Privacy → Privacy Policy URL**.
3. Complete App Privacy answers for Google Mobile Ads and Game Center based on Google's current SDK disclosures. The app ships no analytics or crash-reporting SDK and collects nothing itself.
4. State that ads are requested as non-personalized. There is no analytics or crash-reporting SDK, so nothing is collected for those purposes.
5. Add support and marketing URLs, copyright holder, category (**Games → Trivia**), description, keywords, screenshots, and review notes.
6. In review notes, explain that Game Center is optional and the home-screen banner uses non-personalized AdMob requests.

## 8. Verify on a device, then submit

There is no Mac in this project, so everything below happens on an iPhone with
a TestFlight build rather than in Xcode. CI has already proved the app compiles,
archives, exports and passes Apple's server-side validation; what follows is the
part CI cannot check.

1. Run the **TestFlight** workflow (section 0) and wait for processing.
2. Install the build on an iPhone from TestFlight.
3. Play a round in several categories. Confirm answer order varies between
   plays of the same question, and that a second round does not repeat the
   first round's questions.
4. Open **World Flags** at each difficulty. Confirm flags render right way up
   and are legible, that Nepal and Switzerland letterbox rather than crop, and
   that no question offers two flags you cannot tell apart.
5. Confirm the banner is Google's **test** banner in a Debug build and a real
   unit only in Release.
6. Sign in with a Game Center sandbox tester, finish rounds in several
   categories, and confirm scores reach the leaderboards.
7. Turn on Airplane Mode and confirm flag rounds still work.
8. Check VoiceOver, Dynamic Type at large sizes, dark mode, the privacy-options
   sheet, local score deletion, and leaving a round part-way through.
9. If the banner reports "Ads unavailable", take a screenshot of the full
   persistent message. Ad failures are non-blocking and no longer appear as a
   transient alert on the Scores tab.
10. Complete export compliance, content rights, age rating, privacy,
   availability, and review-contact fields in App Store Connect.
11. Submit the selected build for review.

## App Store Connect API key security and optional automation

Any App Store Connect `.p8` private key pasted into chat, email, an issue, or a PR must be treated as compromised and revoked immediately in **App Store Connect → Users and Access → Integrations → App Store Connect API**. Do not reuse that key.

Most Game Center and app-record work can be automated with a fresh App Store Connect API key, but account-owner actions and some review questionnaires may still require the web interface. If automation is desired later, create a new dedicated key with the minimum **App Manager** access and transfer the **Issuer ID**, **Key ID**, and `.p8` file through a secure secret/file-transfer mechanism. Never commit the `.p8` file or paste its contents into chat.
