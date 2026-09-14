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
the mode used by ordinary CI. When the AdMob variables are absent, the build
uses Google's official sample Android app/banner IDs so CI can compile, shrink,
and package the advertising code without committing production credentials.
Production releases must provide the Android-specific real IDs.

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

After the first bundle is accepted, Play Console exposes the app-signing
certificate fingerprint. That fingerprint is also required for verified Friend
Challenge app links.

## 4. Android ads and consent

Create a separate **Android** EZ Trivia app in AdMob for package
`com.rsm.eztrivia`; do not reuse the iOS AdMob app ID. Create a banner ad unit
and store both resulting IDs as repository secrets:

- `ANDROID_ADMOB_APP_ID`
- `ANDROID_ADMOB_BANNER_ID`

The app requests advertising consent through Google's User Messaging Platform
before making its first ad request. Configure the applicable Privacy & messaging
message in AdMob before production testing. The banner is suppressed whenever
UMP says ads cannot yet be requested and after the Remove Ads entitlement is
owned.

The GitHub Internal Testing workflow passes these secrets into Gradle when they
exist. A build without them is deliberately a sample-ad build and must not be
promoted to production.

## 5. Google Play Billing — Remove Ads

Create and activate a one-time Google Play product with this exact product ID:

    com.rsm.eztrivia.removeads

It should behave as a non-consumable entitlement: the app never consumes the
purchase. Configure its default purchase option, localized title/description,
and price in Play Console. The Android client queries Google Play for current
ownership, acknowledges completed purchases, supports pending purchases, and
provides a restore/recheck action. A successful ownership query is authoritative,
so a refund or revocation removes the cached entitlement again.

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
`https://killjoy00.github.io/EZTrivia/challenge.html` intent filter, so a
challenge link shared from an iOS player can open the Android app directly.

Verification only succeeds once the **domain** publishes a Digital Asset Links
file naming this app's signing certificate. Note the path: it is served from the
domain root, not from this project's `/EZTrivia/` Pages path, so it belongs in
the `killjoy00.github.io` repository, not this one.

    https://killjoy00.github.io/.well-known/assetlinks.json

Contents:

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.rsm.eztrivia",
      "sha256_cert_fingerprints": ["PASTE_SHA256_FINGERPRINT_HERE"]
    }
  }
]
```

Use the **app signing certificate** fingerprint, not the upload certificate:
Play Console → your app → Test and release → Setup → App signing → *SHA-256
certificate fingerprint*. Colons included, uppercase hex.

Until that file is live with the right fingerprint, the link still works — it
just opens in a browser. Nothing regresses in the meantime.

To check verification on a device once it is published:

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
- Android AdMob/UMP code path and Google Play Billing Remove Ads code path; the
  real AdMob IDs, UMP message, and Play product still require console setup and
  runtime validation before production submission.
