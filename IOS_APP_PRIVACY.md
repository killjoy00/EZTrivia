# EZ Trivia iOS — App Store Connect privacy answers

Reviewed against the signed [iOS build 36](https://github.com/killjoy00/EZTrivia/actions/runs/44806956588) archive (Google Mobile Ads **12.14.0**, Google UMP **3.1.0**), the app's iCloud/GameKit code, and Apple's [App privacy details](https://developer.apple.com/app-store/app-privacy-details/).

**Does the app collect data?** Yes, via Google ads/consent SDKs and the app's saved-game sync.

| Data type | Select purposes | Linked to identity? | Used for tracking? |
| --- | --- | --- | --- |
| Usage Data → Product Interaction | Third-Party Advertising; Analytics; App Functionality | Yes | No |
| Usage Data → Advertising Data | Third-Party Advertising; Analytics | Yes | No |
| Identifiers → Device ID | Third-Party Advertising; Analytics | Yes | **Yes** |
| Location → Coarse Location | Third-Party Advertising; Analytics; App Functionality | Yes | No |
| Diagnostics → Crash Data | Analytics | No | No |
| Diagnostics → Performance Data | Third-Party Advertising; Analytics; App Functionality | No | No |
| Diagnostics → Other Diagnostic Data | Third-Party Advertising; Analytics | No | No |
| User Content → Gameplay Content | App Functionality (iCloud/game scores) | Yes | No |

**Remove Other Usage Data**: neither bundled Google SDK manifest declares it, and the app ships no first-party telemetry backend.

**Do not select Developer's Advertising or Marketing** for EZ Trivia's current use (it only displays third-party banner ads). Google's generic Mobile Ads manifest includes that purpose for multiple SDK data types; this table reflects actual EZ Trivia usage instead of every optional Google partner use. Reassess if you start advertising your own products with player data.

No Product Personalization or Other Purposes disclosures are needed based on current code.

**Device ID / tracking:** Google's SDK declares Device ID as linked and used for tracking. ATT authorization is requested after regional UMP consent and before Google Mobile Ads initializes. Apple users who decline ATT can still receive ads, but Google says IDFA is not sent in those requests.

**Privacy policy URL:** https://killjoy00.github.io/EZTrivia/privacy.html
**User Privacy Choices URL:** Blank (optional; app has UMP options in Settings where required).

**Important:** Changing repository files does *not* update App Store Connect's separate questionnaire. Check that disclosures match the current public App Store version before publishing.

## Real iPhone acceptance checklist

1. With tracking requests enabled in iOS Settings, verify relevant EU/US UMP consent comes before the native Apple ATT permission on a fresh installation.
2. Test **Ask App Not to Track**: gameplay works, no IDFA in Google requests; banner may depend on AdMob inventory.
3. Test **Allow** separately: no duplicate permission prompt. An already-determined ATT status must not prompt again.
4. After buying **Remove Ads**, confirm no banner or ATT request is shown at app launch.
5. Confirm **Ad privacy choices** stays accessible where required.
6. Validate the signed archive includes the ATT usage description, AppTrackingTransparency framework, and app + Google SDK privacy manifests.

These runtime checks require a device; successful CI proves the app compiles and archives but does not simulate consent-region or ATT UI decisions.
