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
Store Connect API client that creates the seventeen classic leaderboards below,
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
4. Create seventeen **Classic Leaderboards**—not a leaderboard set or recurring leaderboard.
5. For every leaderboard, choose:
   - **Sort order:** High to Low
   - **Score range:** 0 through 1,000,000 for category boards; 0 through 2,000 for Daily Challenge
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
| TV High Scores | `EZTrivia.tv` | TV High Scores |
| Geography High Scores | `EZTrivia.geography` | Geography High Scores |
| Music High Scores | `EZTrivia.music` | Music High Scores |
| Animals High Scores | `EZTrivia.animals` | Animals High Scores |
| Food & Drink High Scores | `EZTrivia.food` | Food & Drink High Scores |
| Books & Literature High Scores | `EZTrivia.literature` | Books & Literature High Scores |
| Art & Architecture High Scores | `EZTrivia.art` | Art & Architecture High Scores |
| Mythology & Legends High Scores | `EZTrivia.mythology` | Mythology & Legends High Scores |
| Video Games High Scores | `EZTrivia.videoGames` | Video Games High Scores |
| Daily Challenge | `EZTrivia.daily` | Daily Challenge |

7. After creating each leaderboard, add its one required **English (U.S.)** localization and enter the third-column display name. “No localization” means no translations; Apple still requires this one default player-facing language.
8. Save every leaderboard.
9. Return to the app version page and associate the Game Center leaderboards with the version if App Store Connect presents that option.

### Add the thirteen achievements

The app displays local progress immediately, but Game Center ignores an
achievement report until an achievement with the exact ID exists in App Store
Connect. These IDs and point values become permanent after release. Create each
achievement with **Hidden: No** and **Achievable More Than Once: No**, then add
an English (U.S.) localization and upload the matching 1024×1024 RGB PNG.

| Display name | Achievement ID (exact) | Points | Pre-earned description | Earned description | Artwork |
| --- | --- | ---: | --- | --- | --- |
| First Round | `EZTrivia.achievement.first_round` | 25 | Complete any round. | You completed your first round. | `Artwork/Achievements/EZTrivia.achievement.first_round.png` |
| Easy Does It | `EZTrivia.achievement.perfect_easy` | 50 | Earn a perfect score on an Easy category round. | You earned a perfect Easy score. | `Artwork/Achievements/EZTrivia.achievement.perfect_easy.png` |
| Perfectly Balanced | `EZTrivia.achievement.perfect_medium` | 75 | Earn a perfect score on a Medium category round. | You earned a perfect Medium score. | `Artwork/Achievements/EZTrivia.achievement.perfect_medium.png` |
| Hard to Beat | `EZTrivia.achievement.perfect_hard` | 100 | Earn a perfect score on a Hard category round. | You earned a perfect Hard score. | `Artwork/Achievements/EZTrivia.achievement.perfect_hard.png` |
| A Little of Everything | `EZTrivia.achievement.all_categories` | 100 | Complete questions from twelve different categories. | You played twelve different categories. | `Artwork/Achievements/EZTrivia.achievement.all_categories.png` |
| Full Spectrum | `EZTrivia.achievement.all_categories_14` | 100 | Complete questions from fourteen different categories. | You played fourteen different categories. | `Artwork/Achievements/EZTrivia.achievement.all_categories_14.png` |
| One-Week Streak | `EZTrivia.achievement.streak_7` | 75 | Complete the Daily Challenge seven days in a row. | You completed a seven-day Daily Challenge streak. | `Artwork/Achievements/EZTrivia.achievement.streak_7.png` |
| Monthly Ritual | `EZTrivia.achievement.streak_30` | 100 | Complete the Daily Challenge thirty days in a row. | You completed a thirty-day Daily Challenge streak. | `Artwork/Achievements/EZTrivia.achievement.streak_30.png` |
| Getting Warmed Up | `EZTrivia.achievement.rounds_10` | 50 | Complete ten rounds. | You completed ten rounds. | `Artwork/Achievements/EZTrivia.achievement.rounds_10.png` |
| Trivia Regular | `EZTrivia.achievement.rounds_50` | 75 | Complete fifty rounds. | You completed fifty rounds. | `Artwork/Achievements/EZTrivia.achievement.rounds_50.png` |
| Century Club | `EZTrivia.achievement.rounds_100` | 100 | Complete one hundred rounds. | You completed one hundred rounds. | `Artwork/Achievements/EZTrivia.achievement.rounds_100.png` |
| Five Figures | `EZTrivia.achievement.points_10000` | 75 | Earn 10,000 lifetime category points. | You earned 10,000 lifetime category points. | `Artwork/Achievements/EZTrivia.achievement.points_10000.png` |
| Point Collector | `EZTrivia.achievement.points_50000` | 75 | Earn 50,000 lifetime category points. | You earned 50,000 lifetime category points. | `Artwork/Achievements/EZTrivia.achievement.points_50000.png` |

The thirteen achievements total 1,000 Game Center points; Apple permits at most
100 points for one achievement and 1,000 across the app. When all metadata and
images are present, include the achievements with the app's Game Center
components submitted for review. Keep `EZTriviaApp/Achievements.swift` and this
table synchronized if wording ever changes.

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

## 6. Finish AdMob banner setup

The Release build already uses:

- App ID: `ca-app-pub-1217971050094766~8213692580`
- Banner ad-unit ID: `ca-app-pub-1217971050094766/8869132851`

Debug builds deliberately use Google's official test IDs so development cannot generate invalid live-ad traffic.

1. Sign in to [Google AdMob](https://admob.google.com/).
2. Open the app matching the production App ID above.
3. Confirm its platform is iOS and its bundle ID is `com.rsm.eztrivia`.
4. Confirm the banner unit above is active.
5. If AdMob offers an App Store association or app-readiness review, associate the existing App Store record and complete that review. Source code cannot bypass an account or app that AdMob has not approved to serve.
6. Open **Privacy & messaging** from AdMob's left navigation. If the page opens on an overview, select the **Messages** tab.
7. Configure and publish every privacy message applicable to the United States and Canada for this app. Select **EZ Trivia**, use the public privacy-policy URL, and keep the required privacy-choice controls enabled.
8. Return to **Privacy & messaging** and confirm each required message is **Published**, not Draft.
9. In **Blocking controls**, select ad categories appropriate for a 4+ general-audience trivia app. Block sensitive categories and review the ad-content rating.
10. Run a Debug build first and confirm Google's test banner appears. Never tap a live ad during development or testing.
11. Install a Release/TestFlight build and confirm a live banner appears above the Play tab bar. A newly activated unit can temporarily return no inventory; retest after AdMob reports the app and unit ready.

`AdConsentManager` runs Google's User Messaging Platform flow and waits for
`canRequestAds` before starting the Mobile Ads SDK. `AdBannerView` then sends a
plain SDK request so Google can honor the consent and regional privacy state
already established by UMP. The app does **not** force `npa=1` on every request.
If the banner remains absent, inspect the AdMob app/unit status, published
privacy messages, and a real-device Release build before changing source.

## 7. App privacy and release metadata

1. Host `PRIVACY.md` at a public HTTPS URL. The GitHub file URL can be used initially, though a stable product website is preferable.
2. In App Store Connect, enter that URL in **App Privacy → Privacy Policy URL**.
3. Complete App Privacy answers for Google Mobile Ads and Game Center based on Google's current SDK disclosures. The app ships no analytics or crash-reporting SDK and collects nothing itself.
4. Describe advertising behavior according to the final UMP and AdMob account configuration; do not claim every request is non-personalized because the code no longer forces that override. There is no analytics or crash-reporting SDK.
5. Add support and marketing URLs, copyright holder, category (**Games → Trivia**), description, keywords, screenshots, and review notes.
6. In review notes, explain that Game Center is optional and the home-screen banner waits for the Google UMP privacy flow before requesting an ad. Note also that notification permission is requested only when a player turns on **Settings → Daily Challenge → Streak reminders**, never at launch, and that the app schedules one local notification with no remote push component.

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
4. Create a Friend Challenge, send its code to a second device, and confirm
   both devices receive the same ten prompts, choices, and answer order. Finish
   it once on the recipient and confirm entering the code again shows the saved
   result rather than starting another attempt.
5. Open **World Flags** at each difficulty. Confirm flags render right way up
   and are legible, that Nepal and Switzerland letterbox rather than crop, and
   that no question offers two flags you cannot tell apart.
6. Confirm the banner is Google's **test** banner in a Debug build and a real
   unit only in Release.
7. Sign in with a Game Center sandbox tester, finish rounds in several
   categories, and confirm the native Scores screens show leaderboard entries
   and synchronized achievement progress. Also confirm the optional full Game
   Center dashboard still opens and dismisses correctly.
8. Enable auto-advance at 2, 5, and 15 seconds. Confirm the countdown resets on
   every answer, a manual Next tap cancels it, and the final question completes
   the round exactly once.
9. Check the Play grid on the smallest supported iPhone and at accessibility
   Dynamic Type sizes. Difficulty percentages must remain horizontal, and the
   grid should switch to one column at accessibility sizes.
10. Turn on Airplane Mode and confirm category, flag, Daily, and code-based
   Friend Challenge rounds still work.
11. Check VoiceOver, dark mode, the privacy-options screen, the ten-row Recent
   Rounds disclosure, local score deletion, and leaving a round part-way through.
12. Complete export compliance, content rights, age rating, privacy,
   availability, and review-contact fields in App Store Connect.
13. Submit the selected build and its new Game Center achievements for review.

## App Store Connect API key security and optional automation

Any App Store Connect `.p8` private key pasted into chat, email, an issue, or a PR must be treated as compromised and revoked immediately in **App Store Connect → Users and Access → Integrations → App Store Connect API**. Do not reuse that key.

Most Game Center and app-record work can be automated with a fresh App Store Connect API key, but account-owner actions and some review questionnaires may still require the web interface. If automation is desired later, create a new dedicated key with the minimum **App Manager** access and transfer the **Issuer ID**, **Key ID**, and `.p8` file through a secure secret/file-transfer mechanism. Never commit the `.p8` file or paste its contents into chat.
