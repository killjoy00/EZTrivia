# Android release setup

The debug build needs nothing beyond the repository and `python3`, and CI proves
it on every push. Two things still have to be done by hand before a Play
release, both because they need secrets or access CI does not have.

## 1. Signing

`assembleRelease` currently produces an **unsigned** APK. CI builds it only to
prove the R8 configuration is sound — a shrinker setup that is never built is
one that breaks on release day.

For Play, use **Play App Signing**: generate an upload keystore, sign locally or
in a release workflow with it, and let Google hold the app signing key. Do not
commit a keystore to this repository.

## 2. App Links for Friend Challenge URLs

`AndroidManifest.xml` declares `android:autoVerify="true"` on the
`https://killjoy00.github.io/EZTrivia/challenge.html` intent filter, so a
challenge link shared from an iOS player opens the Android app directly instead
of the browser.

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
just opens in a browser, exactly as it did before `autoVerify` was added.
Nothing regresses in the meantime.

To check verification on a device once it is published:

    adb shell pm get-app-links com.rsm.eztrivia

## What is already handled

- Launcher icons, generated from the iOS app icon by
  `Scripts/make_android_icons.py`. Rerun it if the iOS icon changes.
- Backup and device-transfer rules, scoped to the two DataStore files.
- Daily reminders re-armed after reboot and after an app update.
- R8 keep rules for kotlinx.serialization, exercised by CI on every push.
