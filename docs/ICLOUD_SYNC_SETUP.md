# iCloud player-state sync setup

EZ Trivia uses Apple's `NSUbiquitousKeyValueStore` to synchronize private gameplay progress across devices signed in to the same iCloud account. There is no EZ Trivia account or custom sync server.

The source side is already configured in this repository:

- `EZTriviaApp/EZTrivia.entitlements` declares `com.apple.developer.ubiquity-kvstore-identifier` as `$(TeamIdentifierPrefix)com.rsm.eztrivia`.
- `ScoreStore` persists locally first, publishes a compact player-state snapshot to iCloud, and merges external iCloud changes.
- Category lifetime points use a shared migration baseline plus per-installation increments so post-upgrade play from multiple devices adds together.

## One-time Apple Developer setup

Before distributing a signed build with this entitlement:

1. Sign in to Apple Developer **Certificates, Identifiers & Profiles**.
2. Open **Identifiers** and select the explicit App ID for `com.rsm.eztrivia`.
3. Edit the App ID and enable the **iCloud** capability.
4. Enable **Key-value storage** for the app. EZ Trivia does not use CloudKit or iCloud Documents for this feature.
5. Save/confirm the App ID change.
6. Any provisioning profiles tied to the modified App ID may need regeneration. The repository's TestFlight workflow uses automatic signing with `-allowProvisioningUpdates`, so a later authenticated archive should request an updated distribution profile automatically.

Apple's current documentation says the `com.apple.developer.ubiquity-kvstore-identifier` entitlement is the entitlement used by iCloud Key-Value Storage and that the Key-value storage service must be enabled for the target/App ID.

## Device verification

After the Apple capability is enabled, verify on two physical devices signed into the same iCloud account:

1. Install the same TestFlight build on both devices.
2. On device A, finish a category round, a Quick Play round, and the Daily Challenge.
3. Give iCloud a short opportunity to propagate, then foreground EZ Trivia on device B.
4. Confirm the category history/lifetime points, Quick Play history, Daily result/streak, seen-question state, and achievement progress appear on device B.
5. Create or complete a Friend Challenge on one device and confirm the one-attempt result appears on the other.
6. Clear recent category rounds on one device and confirm an older cloud snapshot does not resurrect them.

If iCloud is unavailable or disabled, the app remains fully playable and continues to persist state locally.
