# Android release setup

The repository can build and validate Android without signing material. A Play
release uses Play App Signing plus a separate upload key held outside the
repository.

## 1. Upload key

Use a PKCS12 upload keystore whose alias is `upload`. Use one password for both
the keystore and the key. Never commit the keystore or its password.

The Gradle release build accepts release inputs through environment variables:

- `ANDROID_UPLOAD_KEYSTORE_PATH` — absolute path to the `.p12` file
- `ANDROID_UPLOAD_KEYSTORE_PASSWORD` — keystore/key password
- `EZTRIVIA_VERSION_CODE` — positive integer; must increase for every Play upload
- `EZTRIVIA_VERSION_NAME` — player-facing version such as `1.0.0`
- `ANDROID_ADMOB_APP_ID` — Android AdMob app ID (`ca-app-pub-...~...`)
- `ANDROID_ADMOB_BANNER_ID` — Android banner ad-unit ID (`ca-app-pub-.../...`)

When the signing variables are absent, the release build stays unsigned. That is
the mode used by ordinary CI. When the AdMob variables are absent, a direct
Gradle build uses Google's official sample Android app/banner IDs so CI can
compile, shrink, and package the advertising code without production inventory.
The signed GitHub Play workflow is stricter: it refuses to publish a Play build
unless both production AdMob IDs are present and belong to the expected EZ
Trivia publisher.

## 2. Build the signed Play bundle

In a trusted release environment with the upload key and production AdMob IDs
available:

```bash
cd android
ANDROID_UPLOAD_KEYSTORE_PATH=/secure/eztrivia-upload.p12 \
ANDROID_UPLOAD_KEYSTORE_PASSWORD='...' \
ANDROID_ADMOB_APP_ID='ca-app-pub-...~...' \
ANDROID_ADMOB_BANNER_ID='ca-app-pub-.../...' \
EZTRIVIA_VERSION_CODE=1 \
EZTRIVIA_VERSION_NAME=1.0.0 \
gradle bundleRelease
```

The Play bundle is written to:

    android/app/build/outputs/bundle/release/app-release.aab

Keep the matching R8 mapping file with the release record:

    android/app/build/outputs/mapping/release/mapping.txt

Upload the `.aab` to Play Console → **Internal testing**. Start at version code
`1`; every later upload must use a larger version code.

## 3. Play App Signing

Use **Play App Signing** in Play Console. The upload key authenticates the bundle
you upload; Google holds and uses the separate app-signing key that signs APKs
delivered to players.

The Play app-signing certificate used by the current Internal Testing build has
already been retrieved from generated APK metadata and verified against the live
Digital Asset Links statement. See `android/DIGITAL_ASSET_LINKS.md`.

## 4. Android ads and consent

Android uses publisher `pub-1217971050094766`, which is also published in the
root `https://killjoy00.github.io/app-ads.txt` seller record. It needs its own
AdMob app entry and Banner unit; do not reuse the iOS app/ad-unit IDs.

AdMob API inventory management cannot use the Play service account. Google
requires OAuth from an authenticated AdMob user. The repository therefore has a
manual **Android AdMob Production** workflow plus
`Scripts/configure_admob_android.py`. After the one-time OAuth secret described
in `android/ADMOB.md` is installed, the workflow can discover the Android app
and Banner unit and attempts to create them if absent. Google's app/ad-unit
create methods are limited-access, so a 403 means the missing inventory must be
created once in the AdMob UI and the workflow re-run for verification.

Store the resulting public SDK identifiers as Actions secrets:

- `ANDROID_ADMOB_APP_ID`
- `ANDROID_ADMOB_BANNER_ID`

The app requests advertising consent through Google's User Messaging Platform
before making its first ad request. Configure and **publish** the applicable
Privacy & messaging message in AdMob before production testing. The banner is
suppressed whenever UMP says ads cannot yet be requested and after the Remove
Ads entitlement is owned.

The signed Internal Testing workflow now requires both production AdMob secrets
and validates that their publisher prefix matches EZ Trivia. Ordinary unsigned
CI still uses Google's sample IDs. See `android/ADMOB.md` for the full account,
OAuth, UMP, and runtime checklist.

## 5. Google Play Billing — Remove Ads

The active one-time Google Play product uses this exact product ID:

    com.rsm.eztrivia.removeads

It behaves as a non-consumable entitlement: the app never consumes the
purchase. The Android client queries Google Play for current ownership,
acknowledges completed purchases, supports pending purchases, and provides a
restore/recheck action. A successful ownership query is authoritative, so a
refund or revocation removes the cached entitlement again.

Billing should be tested with a license tester using an app installed from a
Google Play testing track; a locally sideloaded build is not a valid end-to-end
purchase test.

## 6. Google Play Games Saved Games

The Android client contains a Google Play Games Saved Games integration for
private cross-device progress. Before production testing, enable **Saved Games**
for the linked Play Games Services project in Play Console. The app uses this
snapshot name:

    eztrivia-player-state-v1

Local DataStore state remains the source used by gameplay. Cloud I/O is
asynchronous: if Play Games or the network is unavailable, the player continues
offline and the app retries after authentication/state changes or a manual sync
from Settings.

Conflicts are resolved manually instead of using a last-device-wins policy. The
cloud envelope carries shared baselines plus per-installation additive counters,
so lifetime points and completed-round totals from two independently played
devices can be added without double-counting the same device on repeated syncs.
Question-progress/achievement facts are monotonic unions; Daily and Friend
Challenge one-attempt records keep the earliest completion; bounded histories
are deduplicated; and clear-history timestamps prevent an older snapshot from
restoring cleared recent/seen state.

Before production, test from Play-installed Internal Testing builds on **two
Android devices signed into the same Play Games profile**:

1. Sync both devices once.
2. Take both offline and make different category/Quick Play progress on each.
3. Complete a Daily/Friend Challenge on one device, and different question
   progress on the other.
4. Reconnect device A and wait for sync, then reconnect device B to force a
   realistic conflict/merge.
5. Re-open both apps and confirm lifetime points, round counters, histories,
   question progress, and one-attempt results converge without loss or duplicate
   counting.
6. Clear recent category history on one device, sync, then verify an older cloud
   copy from the other device cannot resurrect the cleared category history or
   seen-question cycle.

The first production release that advertises Android cross-device progress must
not ship until Saved Games is enabled and this two-device test passes.

## 7. App Links for Friend Challenge URLs

`AndroidManifest.xml` declares `android:autoVerify="true"` on the
`https://killjoy00.github.io/EZTrivia/challenge.html` intent filter.

The root-domain Digital Asset Links statement is live and verified at:

    https://killjoy00.github.io/.well-known/assetlinks.json

It names package `com.rsm.eztrivia`, relation
`delegate_permission/common.handle_all_urls`, and the Google Play app-signing
SHA-256 certificate. `android/DIGITAL_ASSET_LINKS.md` records the fingerprint and
the automated verification run. Re-run the manual verifier after any app-signing
key rotation, package/domain change, or assetlinks edit.

To check verification on a device:

    adb shell pm get-app-links com.rsm.eztrivia

## What CI proves before release

Ordinary Android CI builds all of the following without signing credentials:

- debug APK
- minified/resource-shrunk release APK
- release Android App Bundle (`.aab`)
- R8 mapping file

That means App Bundle packaging, Play Billing, Google Mobile Ads/UMP, Play Games
Saved Games code, and the shrinker run on every relevant PR rather than being
discovered for the first time during a Play upload. Unit tests also exercise the
Saved Games merge contract without requiring live Play Games credentials.

## What is already handled

- Package/application ID: `com.rsm.eztrivia`.
- Target SDK 36.
- Launcher icons, generated from the iOS app icon by
  `Scripts/make_android_icons.py`.
- Backup and device-transfer rules, scoped to the two DataStore files.
- Daily reminders re-armed after reboot and after an app update.
- R8 keep rules for kotlinx.serialization.
- Google Play Games v2 authentication, achievements, and leaderboards.
- Conflict-safe Google Play Games Saved Games client integration and local merge
  bookkeeping; Play Console Saved Games enablement and real two-device runtime
  validation still remain before production.
- Android AdMob/UMP code path and Google Play Billing Remove Ads code path.
- The Google Play Remove Ads product is active; runtime purchase/restore/refund
  validation still remains.
- AdMob inventory automation and a production-ID release guard are present; the
  authenticated AdMob inventory run, published Privacy & messaging configuration,
  and Play-installed ad/entitlement runtime test remain before production.
