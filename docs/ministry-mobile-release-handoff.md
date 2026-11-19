# Ministry mobile releases through EZTrivia

This repository already owns the shared mobile app publishing credentials used
for EZTrivia, StarRun, and Ultimate Tic/Tac/Toe. Keep them here.

**The Ministry of Coincidences** lives in `killjoy00/ministry`. Its game CI
and touch-first Godot 4.7.2 implementation are independent. Only the approved
store-release builds are orchestrated by **EZTrivia**.

## Identity

- Android package / iOS bundle ID: `org.killjoy00.ministry`
- App name: The Ministry of Coincidences
- Apple Team ID: `3564X3VTDB`
- Initial beta version: `0.1.0`
- Source: an exact immutable commit SHA **already merged** into Ministry main
- Destination: Play **internal** or TestFlight; never public production

## Reuse of existing secrets

Android: `ANDROID_UPLOAD_KEYSTORE_BASE64`,
`ANDROID_UPLOAD_KEY_PASSWORD` (keystore alias `upload`),
`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.

Apple: `ASC_KEY_ID`, `ASC_ISSUER_ID`,
`ASC_KEY_P8`, `APPLE_TEAM_ID`.

These names are **already** used in EZTrivia for other apps. No copy of
these credentials is ever needed in the Ministry repository.

## Release workflows

- `.github/workflows/ministry-play-internal.yml` — uses existing Play signing
  to generate a release AAB, verifies package identity, and optionally uploads
  only to Google Play internal testing.
- `.github/workflows/ministry-testflight.yml` — uses existing Apple API
  credentials to archive the approved Godot iOS project and upload to
  TestFlight.
- Both may be started from GitHub Actions with a SHA from Ministry main.
  Their versions and app identity are checked before signing.
- The workflows also accept a **reviewed main-branch request JSON**:
  `.github/ministry-android-release-request.json` or
  `.github/ministry-testflight-request.json`. Committing an approved
  request to EZTrivia main triggers the corresponding release automatically.
  Do not create those request files until Ministry PRs #14, #16, #17, #18
  are all merged and release checks have passed.

## Google Play one-time app settings

The shared upload certificate must be accepted by the new Ministry app in
Google Play App Signing, and the existing Play API service account must be
granted access to that app. These settings are app-specific. Reusing signing
infrastructure does **not** guarantee the new Play app automatically trusts
the key or authorizes the service account.

If Google Play returns a certificate mismatch, inspect
**Play Console → Setup → App integrity → Upload key certificate**.
Do not regenerate the long-lived EZTrivia keystore or reset keys for other
apps. If the service account is not granted Ministry app permissions, add
that app in Play Console API access.

## Request examples (only after the Ministry SHA is on main)

Android request:

```json
{
  "enabled": true,
  "source_sha": "<40-char merged Ministry main commit>",
  "version_code": 1,
  "version_name": "0.1.0",
  "release_notes": "Initial Ministry mobile beta.",
  "publish_internal": false
}
```

Start with `publish_internal=false` to verify signed candidate packaging.
After confirming the Play upload certificate and API access, set it to
`true` in a reviewed request to upload only to internal testing.

iOS request:

```json
{
  "repository": "killjoy00/ministry",
  "bundleID": "org.killjoy00.ministry",
  "target": "testflight",
  "sha": "<40-char merged Ministry main commit>",
  "version": "0.1.0"
}
```

The TestFlight workflow archives and uploads when a request is committed.
Only create that request once a private TestFlight upload is explicitly
intended. A successful Xcode upload can still be followed by Apple processing
or beta review before testers can install it.

## Validation and controls

- Keep PR checks in Ministry for the game engine, rules and mobile UI.
- No signing of arbitrary PR heads or branches: release workflows require
  a SHA that is a Git ancestor of `ministry/main`.
- Do not transfer secrets across repositories or commit keystores/Apple .p8.
- A signed AAB from EZTrivia is **not** a public Play release unless the
  release track is explicitly changed; these workflows only use `internal`.
- A successful TestFlight upload is **not** App Store submission.
- Test portrait/landscape, safe areas, touch targets, state restoration,
  and mobile Causality Theater on real Android/iOS devices before release.
