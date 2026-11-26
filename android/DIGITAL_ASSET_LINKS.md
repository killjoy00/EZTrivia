# Google Play Digital Asset Links

Last verified: September 14, 2026

Package: `com.rsm.eztrivia`

Live statement:

`https://killjoy00.github.io/.well-known/assetlinks.json`

## Verified app-signing certificate

Google Play's `generatedApks.list` API was queried for the Internal Testing release and returned this app-signing SHA-256 certificate fingerprint:

`C7:95:FE:76:2A:16:A2:42:2C:55:12:5F:BE:64:42:88:7D:1F:58:81:BB:AC:6F:0D:09:22:AA:E9:69:AA:C4:BD`

The live `assetlinks.json` publishes the same fingerprint for `com.rsm.eztrivia` with the relation `delegate_permission/common.handle_all_urls`.

Validation run `34897560439` completed successfully. It discovered version code `2` from Google Play, fetched generated APK signing metadata, fetched the live Digital Asset Links statement, and verified that every signing fingerprint returned by Google Play was present on the website.

## Hosting

The Digital Asset Links statement is maintained in the public repository `killjoy00/killjoy00.github.io` at `.well-known/assetlinks.json`.

GitHub Pages initially returned HTTP 404 for the dot-prefixed `.well-known` path. A root `.nojekyll` file was added so GitHub Pages serves the directory directly. Pages deployment run `34897532360` completed successfully, after which the live statement became reachable and the automated verification passed.

## Why this fingerprint matters

For a Play-distributed build, Android App Links must trust the certificate Google Play uses to sign the APK delivered to users. That is the **Play app-signing key**, not the upload key used to sign the AAB before upload.

The verification workflow intentionally retrieves signing metadata from Google Play rather than deriving it from the repository's upload keystore.

## Reverification

`.github/workflows/verify-digital-asset-links.yml` is manual-only. Re-run it whenever:

- Play App Signing keys are upgraded or rotated;
- Google Play begins returning an additional signing certificate for generated APKs;
- the package name or app-link domain changes;
- the GitHub Pages hosting configuration changes; or
- `assetlinks.json` is edited.

The workflow discovers current version codes from the Internal Testing track and uploaded bundles, checks all generated APK metadata it can retrieve, and fails if any Google Play signing fingerprint is absent from the live statement.
