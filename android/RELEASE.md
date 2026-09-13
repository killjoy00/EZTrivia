# Android release setup

The repository can build and validate Android without signing material. A Play
release uses Play App Signing plus a separate upload key held outside the
repository.

## 1. Upload key

Use a PKCS12 upload keystore whose alias is `upload`. Use one password for both
the keystore and the key. Never commit the keystore or its password.

The Gradle release build accepts signing material only through environment
variables:

- `ANDROID_UPLOAD_KEYSTORE_PATH` — absolute path to the `.p12` file
- `ANDROID_UPLOAD_KEYSTORE_PASSWORD` — keystore/key password
- `EZTRIVIA_VERSION_CODE` — positive integer; must increase for every Play upload
- `EZTRIVIA_VERSION_NAME` — player-facing version such as `1.0.0`

When the signing variables are absent, the release build stays unsigned. That is
the mode used by ordinary CI.

## 2. Build the signed Play bundle

In a trusted release environment with the upload key available:

```bash
cd android
ANDROID_UPLOAD_KEYSTORE_PATH=/secure/eztrivia-upload.p12 \
ANDROID_UPLOAD_KEYSTORE_PASSWORD='...' \
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

## 4. App Links for Friend Challenge URLs

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

That means App Bundle packaging and the shrinker run on every relevant PR rather
than being discovered for the first time during a Play upload.

## What is already handled

- Package/application ID: `com.rsm.eztrivia`.
- Target SDK 36.
- Launcher icons, generated from the iOS app icon by
  `Scripts/make_android_icons.py`.
- Backup and device-transfer rules, scoped to the two DataStore files.
- Daily reminders re-armed after reboot and after an app update.
- R8 keep rules for kotlinx.serialization.
