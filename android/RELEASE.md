# Android release setup

The repository can build and validate Android without secrets. A Play release
uses Play App Signing plus a separate upload key held outside the repository.

## 1. One-time upload-key setup

The release workflow expects a PKCS12 upload keystore whose alias is `upload`.
Use one password for both the keystore and the key. Never commit the keystore.

Add exactly these repository secrets in GitHub:

- `ANDROID_UPLOAD_KEYSTORE_BASE64` — base64 text of the `.p12` file
- `ANDROID_UPLOAD_KEY_PASSWORD` — the keystore/key password

The workflow materializes the keystore only inside the ephemeral GitHub Actions
runner, verifies the `upload` alias, builds the signed Android App Bundle, then
uploads the bundle as an Actions artifact. The keystore itself is never uploaded
as an artifact.

The Gradle release build remains unsigned when those environment variables are
absent, so ordinary pull-request CI does not require signing credentials.

## 2. Build a Play bundle

After the two secrets exist:

1. GitHub → `killjoy00/EZTrivia` → **Actions**.
2. Open **Android Play Release**.
3. Tap **Run workflow** and leave the branch on `main`.
4. Enter a `version_code`. It must be a positive integer and must increase for
   every bundle uploaded to Play. Start with `1`.
5. Enter a `version_name`. Start with `1.0.0`.
6. Run the workflow.
7. Download the artifact named `eztrivia-play-v<version_code>`.
8. Upload `app-release.aab` to Play Console → **Internal testing**.

The artifact also contains `release-info.txt` and the R8 `mapping.txt` file. Keep
`mapping.txt` with the release record so obfuscated crash reports can be decoded.

## 3. Play App Signing

Use **Play App Signing** in Play Console. The upload key above authenticates the
bundle uploaded to Google; Google holds and uses the separate app-signing key
that signs APKs delivered to players.

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

Ordinary Android CI now builds all of the following without signing credentials:

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
