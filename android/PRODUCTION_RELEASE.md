# Android Production release path

Package: `com.rsm.eztrivia`

This document records the safe path from an already accepted Google Play Internal Testing build to Production. The repository must not upload or promote a build that contains Google's sample AdMob IDs.

## Automated Production promotion

The manual-only **Android Play Production Promotion** workflow uses the existing `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` service account and Android Publisher v3. It does **not** build or upload a new AAB.

It has three modes:

- `audit`: read current bundles, Internal/Production tracks, and Play Games publication metadata and report whether a candidate is eligible. It never changes a track or publishes Play Games resources.
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
- the version is at least `versionCode 3`;
- all 19 compiled Play Games achievements expose published metadata;
- all 17 compiled Play Games leaderboards expose published metadata.

The minimum version rule is intentional. The currently accepted Internal `versionCode 2` predates the repository hardening that makes signed Play builds fail unless `ANDROID_ADMOB_APP_ID` and `ANDROID_ADMOB_BANNER_ID` exist and belong to publisher `pub-1217971050094766`. Therefore versionCode 2 is **not** treated as a safe Production candidate by automation. Do not burn a new versionCode until the production AdMob IDs exist and a new Internal build is actually needed.

The Play Games publication rule is also intentional. Google states that an unpublished Play Games Services project only works for allowlisted testers; other accounts can receive OAuth/404 failures at platform authentication. EZ Trivia therefore keeps the safe order:

1. test Play Games + Saved Games on Play-installed Internal builds;
2. publish the Play Games Services configuration;
3. verify the Production audit reports 19/19 achievements and 17/17 leaderboards published;
4. only then validate/promote the Android app to Production.

The Production workflow **does not publish PGS automatically**. This preserves the project rule that runtime testing comes first.

### Play Games publication API boundary

The documented Google Play Games Services Publishing API currently exposes configuration resources for achievements, leaderboards, and their images. Its public reference does **not** expose an application/game-level method that publishes all draft game changes. Google's current “Test and publish your game” instructions direct developers to the Play Games Services **Publishing** page in Play Console and to click **Publish** there.

Therefore the current supported split is:

- automate and audit achievement/leaderboard configuration through the PGS Publishing API;
- complete runtime testing first;
- perform the actual game-level PGS publication in Play Console;
- rerun the automated audit afterward and require 19/19 + 17/17 published before app Production validation/promotion.

Do not invent or call an undocumented publication endpoint merely to avoid that one Console action.

References:

- https://developer.android.com/games/services/publishing/api
- https://developer.android.com/games/pgs/console/publish

For a real launch candidate, first build/upload the new version through `Android Play Internal Release`, which now enforces production AdMob inventory. Complete Play-installed runtime QA on that exact version. Then run Production Promotion in `audit`, then `validate`, and only then `promote` when the remaining launch gates are complete.

## Google Play App content API audit

As of September 15, 2026, the public Google Play Android Publisher v3 reference exposes the Google Play-hosted Data Safety submission endpoint (`applications.dataSafety`) but does not expose Google Play-hosted endpoints for these remaining App content forms:

- Ads declaration;
- App access;
- Advertising ID;
- Target audience and content;
- IARC Content rating.

Google also documents a newer **App Store Review API** that contains policy declarations for ads, access details, Advertising ID, target audience, and similar fields. That API is specifically for third-party app stores participating in Google's third-party app-store program and the apps those stores host. It is not the API for editing EZ Trivia's Google Play Console App content declarations, so it must not be used as a workaround here.

Result: Data Safety remains automated; the forms above remain Play Console completion/verification steps unless Google adds a supported Google Play-hosted API.

Official references:

- https://developers.google.com/android-publisher/api-ref/rest
- https://developers.google.com/android-publisher/app-store-review

## App access / reviewer path

Core EZ Trivia gameplay does not require an EZ Trivia account, membership, subscription, location gate, or developer-issued login. Google Play Games is optional and the local/offline game remains usable if Play Games authentication is unavailable.

However, achievements, leaderboards, and Saved Games are authenticated Google Play Games features. While the PGS project is unpublished, those features are restricted to allowlisted PGS testers. That state is **not suitable for final Production review**, because Google's documentation says non-tester accounts can receive OAuth/404 failures against unpublished PGS endpoints.

After PGS is published, the reviewer path is:

1. launch EZ Trivia; no app-specific login is required;
2. all normal trivia modes, Scores, Settings, Daily Challenge, Friend Challenge, and Remove Ads UI are reachable without an EZ Trivia account;
3. for optional Play Games features, use the Google Play Games profile configured on the review device;
4. if automatic Play Games authentication does not complete, open **Settings > Google Play Games > Connect Google Play Games**;
5. achievements, leaderboards, and Saved Games then use that Play Games identity. EZ Trivia has no separate username/password to provide.

Google's current review guidance says that if all or part of an app is restricted by authentication, the App access / Sign-in details declaration must provide enough instructions and access resources for review. Because EZ Trivia's restricted features use Google's own Play Games identity rather than a developer-run account, the production declaration should explicitly explain this path. Do not claim that unpublished PGS features are publicly accessible, and do not provide a personal production-user credential as a workaround.

References:

- https://support.google.com/googleplay/android-developer/answer/9859455
- https://support.google.com/googleplay/android-developer/answer/15748846
- https://developer.android.com/games/pgs/console/publish

## Advertising ID evidence and declaration

EZ Trivia targets Android API 36 and depends on Google Mobile Ads SDK `25.4.0`. Google Mobile Ads versions 20.4.0 and newer declare `com.google.android.gms.permission.AD_ID` in the SDK library manifest, so Android's manifest merger places that permission in the packaged app even though EZ Trivia's source `AndroidManifest.xml` does not declare it directly.

This is not theoretical: the release APK produced by Android CI on September 15, 2026 was independently inspected and contains `com.google.android.gms.permission.AD_ID`. The Android CI workflow now repeats that check on every release APK and also asserts that the packaged application ID is `com.rsm.eztrivia`, target SDK is `36`, the release is not debuggable, and no fine/coarse Android location permission is present.

For the Google Play **Advertising ID** declaration, the repository evidence therefore supports:

- **Does the app use advertising ID?** Yes.
- **Purposes:** Advertising or marketing; Analytics; Fraud prevention, security, and compliance.

Those purposes match Google's current data-disclosure documentation for Google Mobile Ads 25.4.0, which says the SDK automatically collects/shares device and account identifiers (including Android advertising ID) for advertising, analytics, and fraud-prevention purposes. Do not select unrelated purposes such as account management or developer communications merely because the form offers them.

If the app later intentionally disables Android ad ID collection (for example by removing the SDK permission through manifest-merger rules), update the Play Advertising ID declaration and Data Safety reasoning in the same change. The CI packaged-manifest guard is intentionally designed to fail first so that such a change cannot happen silently.

References:

- https://support.google.com/googleplay/android-developer/answer/6048248
- https://developers.google.com/admob/android/privacy/play-data-disclosure

## Gates before the Production commit

Do not run `promote` until the exact candidate version has passed the launch gates that cannot be proven by Android Publisher track metadata alone:

- production AdMob app/banner IDs are baked into the signed Internal build;
- AdMob Privacy & messaging is published;
- Play-installed consent/banner/Remove Ads purchase/restore/refund behavior is validated;
- Saved Games is enabled and the two-device offline conflict/reconnect test passes;
- Play Games Services runtime testing passes and the PGS configuration is published;
- Ads, App access, Advertising ID, Target audience, and Content rating are complete;
- final Production country/device availability is reviewed in Play Console.

The workflow intentionally does not pretend those external/manual gates are machine-verifiable when the relevant public API does not expose them. It does, however, block Production when the Play Games Publishing API still shows compiled achievements or leaderboards without published metadata.
