# Android Production release path

Package: `com.rsm.eztrivia`

This document records the safe path from an already accepted Google Play Internal Testing build to Production. The repository must not upload or promote a build that contains Google's sample AdMob IDs.

## Automated Production promotion

The manual-only **Android Play Production Promotion** workflow uses the existing `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` service account and Android Publisher v3. It does **not** build or upload a new AAB.

It has three modes:

- `audit`: read current bundles and Internal/Production tracks and report whether a candidate is eligible. It never changes a track.
- `validate`: stage the exact intended Production track update inside a disposable Play edit, call `edits.validate`, and delete the edit without committing it.
- `promote`: perform the same guards and validation, then commit the edit only when `confirm_production` is explicitly true.

The guard refuses to promote a version unless all of the following are true:

- the version is an accepted Play bundle;
- it is present in a **completed** Internal Testing release;
- it is the highest completed Internal version;
- the Internal release has the normal repository workflow release-name marker `(v<versionCode>)`;
- Production does not already contain that version;
- there is no draft, halted, or in-progress Production release that the workflow could accidentally overwrite;
- Production does not already contain a higher version;
- the version is at least `versionCode 3`.

The minimum version rule is intentional. The currently accepted Internal `versionCode 2` predates the repository hardening that makes signed Play builds fail unless `ANDROID_ADMOB_APP_ID` and `ANDROID_ADMOB_BANNER_ID` exist and belong to publisher `pub-1217971050094766`. Therefore versionCode 2 is **not** treated as a safe Production candidate by automation. Do not burn a new versionCode until the production AdMob IDs exist and a new Internal build is actually needed.

For a real launch candidate, first build/upload the new version through `Android Play Internal Release`, which now enforces production AdMob inventory. Complete Play-installed runtime QA on that exact version. Then run Production Promotion in `audit`, then `validate`, and only then `promote` when the remaining launch gates are complete.

## Google Play App content API audit

As of September 15, 2026, the public Google Play Android Publisher v3 reference exposes the Google Play-hosted Data Safety submission endpoint (`applications.dataSafety`) but does not expose Google Play-hosted endpoints for these remaining App content forms:

- Ads declaration;
- App access;
- Target audience and content;
- IARC Content rating.

Google also documents a newer **App Store Review API** that contains policy declarations for ads, access details, target audience, and similar fields. That API is specifically for third-party app stores participating in Google's third-party app-store program and the apps those stores host. It is not the API for editing EZ Trivia's Google Play Console App content declarations, so it must not be used as a workaround here.

Result: Data Safety remains automated; the four forms above remain Play Console completion/verification steps unless Google adds a supported Google Play-hosted API.

Official references:

- https://developers.google.com/android-publisher/api-ref/rest
- https://developers.google.com/android-publisher/app-store-review

## Gates before the Production commit

Do not run `promote` until the exact candidate version has passed the launch gates that cannot be proven by Android Publisher track metadata alone:

- production AdMob app/banner IDs are baked into the signed Internal build;
- AdMob Privacy & messaging is published;
- Play-installed consent/banner/Remove Ads purchase/restore/refund behavior is validated;
- Saved Games is enabled and the two-device offline conflict/reconnect test passes;
- Play Games Services runtime testing passes and the PGS configuration is published;
- Ads, App access, Target audience, and Content rating are complete;
- final Production country/device availability is reviewed in Play Console.

The workflow intentionally does not pretend those external/manual gates are machine-verifiable when the relevant public API does not expose them.
