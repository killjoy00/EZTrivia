# Android monetization QA

Package: `com.rsm.eztrivia`

This is the release evidence checklist for the Android banner, UMP consent flow, and the one-time Google Play **Remove Ads** product `com.rsm.eztrivia.removeads`.

The checklist deliberately separates machine-verifiable configuration from behavior that only Google Play can exercise with a Play-installed build and a licensed tester account. Do not mark a real purchase, pending purchase, refund, or revocation as tested based only on API inventory.

## Automated release evidence

A candidate is not ready for Production unless CI / repository audits establish all of the following:

| Check | Expected evidence |
| --- | --- |
| Production AdMob app ID | `ca-app-pub-1217971050094766~1085041409` |
| Production banner ID | `ca-app-pub-1217971050094766/1190439457` |
| Google Mobile Ads SDK | Present in the release build |
| UMP SDK | Present and `AdConsentManager` gates ad initialization/requests on `canRequestAds()` |
| Advertising ID declaration evidence | Release manifest contains the SDK-provided `com.google.android.gms.permission.AD_ID` |
| Remove Ads product ID | `com.rsm.eztrivia.removeads` |
| Remove Ads Play inventory | Active one-time product with an active purchase option |
| Billing SDK | Present in the release build |
| Purchase acknowledgement | Purchased entitlement is acknowledged through Google Play Billing |
| Authoritative restore/revocation check | Successful `queryPurchasesAsync(INAPP)` overwrites the cached entitlement |
| Ad suppression | `AdBanner` returns without requesting an ad when `hasRemovedAds` is true |
| Release IDs | Signed Play workflows fail rather than silently use Google's sample AdMob IDs |

The existing Play/AdMob audit workflows and Android CI provide the configuration evidence. The client additionally exposes non-sensitive runtime state in a **Report a problem** email draft so a tester can report whether Billing connected, the product loaded, ownership was checked, UMP permitted ads, Mobile Ads initialized, and the banner loaded or failed.

## Real Play-installed test matrix

Run these checks on the exact Play-delivered candidate before Production. Use licensed tester accounts and Google Play's supported test-payment instruments; never use a production card merely to prove the flow.

| Scenario | Steps | Pass condition |
| --- | --- | --- |
| Fresh non-purchaser | Install from the testing track and open Play screen | Core UI works; consent flow behaves for the tester's region; banner is not requested before UMP permits it |
| Eligible banner | After consent permits ads, return to Play screen | Banner loads above app navigation; no gameplay screen contains an ad |
| Privacy choices | Where UMP reports privacy options required, open Settings | **Ad privacy choices** appears and opens Google's privacy-options form |
| Purchase cancel | Start Remove Ads and cancel the Play purchase sheet | App returns normally; entitlement remains false; purchase button remains usable |
| Successful purchase | Complete the Google Play test purchase | Entitlement becomes true; banner disappears; Settings reports Ads removed |
| Relaunch ownership | Force-stop and relaunch after purchase | Cached entitlement prevents an ad flash; Play ownership refresh keeps entitlement true |
| Restore | Clear app data or install on another eligible device/account state, then choose Restore purchases | Google Play ownership restores Remove Ads without charging again |
| Pending purchase | Use Google's pending test payment method | UI shows purchase pending; entitlement remains false until Play changes it to purchased |
| Pending resolves | Complete the pending test purchase in Play | Entitlement becomes true and purchase is acknowledged |
| Refund/revocation | Refund/revoke the test purchase using Google Play's supported test tooling, then resume/relaunch | Successful ownership refresh clears entitlement and ads become eligible again subject to consent |
| Ad load failure | Exercise offline/no-network or another expected failure state | App remains usable; no crash/layout break; problem report records banner failure state |
| Purchased + privacy | With Remove Ads owned, relaunch | App does not initiate advertising consent solely to show an ad that will never be requested |

## Tester problem-report evidence

Settings → **Report a problem** creates a user-controlled email draft containing:

- app version name/code;
- Android version/API level;
- device manufacturer/model and locale;
- Billing connection readiness;
- whether Remove Ads product details loaded;
- whether an ownership query completed;
- purchased/pending state;
- whether UMP currently permits ad requests;
- whether Mobile Ads initialized;
- banner state: not requested, loading, loaded, or failed; and
- SDK error messages when present.

The tester can edit or delete the diagnostic block before sending. Nothing is transmitted until the tester explicitly sends the email.

## Release rule

Do not promote a candidate to Production until the automated evidence is green and the real Play-installed matrix above has been exercised on the candidate version. If a test fails, fix it in a new versionCode on the same testing track; do not alter the closed-test tester roster merely to test a new build.
