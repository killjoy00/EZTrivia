# Android AdMob production setup

Package: `com.rsm.eztrivia`

Publisher used by the public root `app-ads.txt` record: `pub-1217971050094766`

Current Android production SDK configuration:

- App ID: `ca-app-pub-1217971050094766~1085041409`
- Banner ad-unit ID: `ca-app-pub-1217971050094766/1190439457`

These IDs are public SDK configuration, not credentials. The signed Play release workflow pins these values so production Android builds do not depend on separate repository secrets for public AdMob identifiers.

The Android client already contains Google Mobile Ads SDK 25.4.0 and UMP 4.0.0. It refreshes consent information at launch, shows a required consent form, exposes Privacy Choices when UMP requires it, and does not initialize/request ads until `canRequestAds()` is true. Remove Ads ownership suppresses the banner.

## Why this cannot use the Google Play service account

The Android Publisher work in this repository uses `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, but AdMob is different: AdMob API requests must be authorized by an authenticated AdMob user through OAuth 2.0. A Play service-account access token therefore cannot be reused for AdMob inventory management.

The repository uses one secret for that user authorization:

`ADMOB_OAUTH_CREDENTIALS_JSON`

Its JSON shape is:

```json
{
  "client_id": "...apps.googleusercontent.com",
  "client_secret": "...",
  "refresh_token": "..."
}
```

Never commit this JSON or paste it into an issue/PR. The resulting AdMob app ID and ad-unit ID are public SDK configuration; the OAuth credentials are not.

## One-time OAuth setup

Use a Google account that has access to the EZ Trivia AdMob publisher account.

1. In Google Cloud project `prefab-faculty-508600-q3`, enable the **AdMob API** if it is not already enabled.
2. Create an OAuth 2.0 **Web application** client (or reuse a suitable user-OAuth client) and allow `https://developers.google.com/oauthplayground` as an authorized redirect URI.
3. Open Google OAuth 2.0 Playground, open its settings, select **Use your own OAuth credentials**, and enter that client ID and client secret.
4. Authorize `https://www.googleapis.com/auth/admob.monetization` with the Google account that owns/has access to AdMob.
5. Exchange the authorization code for tokens and copy the refresh token.
6. In this repository's Actions secrets, create `ADMOB_OAUTH_CREDENTIALS_JSON` containing the client ID, client secret, and refresh token in the JSON shape above.

The refresh token is used only to mint short-lived AdMob API access tokens during the manual workflow.

## Automated inventory setup

Run **Actions → Android AdMob Production → Run workflow**.

The workflow runs `Scripts/configure_admob_android.py`, which:

1. verifies `https://killjoy00.github.io/app-ads.txt` contains the expected Google seller record for the selected publisher;
2. exchanges the stored user OAuth refresh token for a short-lived access token;
3. lists Android AdMob apps under the publisher and looks first for one linked to `com.rsm.eztrivia`, then for a single manual Android app named `EZ Trivia`;
4. if the app is missing and `create_missing` is enabled, attempts to create it through AdMob API v1beta;
5. lists banner units for the selected app and creates `EZ Trivia Android Banner` if missing;
6. validates that returned app/ad-unit IDs belong to the expected publisher; and
7. archives a non-secret JSON result containing the production IDs, app approval state, and Play-store-link state.

Google currently marks both AdMob `apps.create` and `adUnits.create` as **limited-access** API methods. If the account returns HTTP 403, create the Android app and/or Banner unit once in the AdMob UI, then re-run this workflow with `create_missing` disabled to verify and capture the IDs. The listing/verification calls are still useful even when creation access is unavailable.

If a Play-linked app cannot be created because the package is not publicly discoverable yet, the script falls back to a manual Android app named `EZ Trivia`. Link that app to the Google Play listing in AdMob once Google makes the listing discoverable; linking an AdMob app to an app-store ID is irreversible, so the script never substitutes a different package.

## Production IDs in signed builds

`android/app/build.gradle.kts` reads:

- `ANDROID_ADMOB_APP_ID`
- `ANDROID_ADMOB_BANNER_ID`

Ordinary CI intentionally falls back to Google's official sample IDs. The signed Play workflow supplies the current production Android IDs listed at the top of this document. The first signed v3 closed-test bundle using those production IDs was successfully uploaded to Google Play on September 16, 2026.

If the Android AdMob app or banner unit changes, update both this document and `.github/workflows/android-play-internal.yml`, then create a new signed Internal Testing build and verify banner/Remove Ads behavior from the Play-delivered build.

## UMP / Privacy & messaging

The code-side UMP integration is already present. Google serves UMP messages based on the AdMob Application ID in the installed app.

The remaining account-side step is in **AdMob → Privacy & messaging**. Create/configure the message types applicable to the app's intended regions and publish them for the Android EZ Trivia app. A message left in Draft is not displayed. This publishing step is managed in the AdMob UI; the public AdMob inventory API used by this repository does not expose message publication.

After publishing, verify on a Play-installed build that:

- consent UI appears when required;
- the Settings privacy-choice entry point appears when UMP says it is required;
- no banner request is made before UMP permits it;
- a banner appears for an eligible non-purchaser;
- Remove Ads ownership suppresses the banner; and
- restoring/revoking/refunding the one-time product updates the entitlement as expected.

## Reverification

Re-run **Android AdMob Production** after changing the AdMob app, banner unit, publisher, Play-store association, or app-ads.txt configuration. The workflow is manual-only because it uses a long-lived user OAuth refresh token and because inventory creation should never happen from an ordinary source push.
