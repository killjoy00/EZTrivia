# Google Play Games Services release state

Application / Cloud project ID: `406580555223`

Android package: `com.rsm.eztrivia`

The Android client uses Google Play Games Services v2 for optional platform authentication, achievements, leaderboards, and private Saved Games synchronization. Local gameplay remains offline-first and does not require Play Games sign-in.

## Reusable configuration audit

The repository contains a manual-only workflow named **Google Play Games configuration audit**. It authenticates with the existing Google Play service account and the `androidpublisher` OAuth scope, calls the Play Games Services Publishing API, and compares every live achievement/leaderboard resource ID with the IDs compiled into `PlayGamesIds.kt`.

`Scripts/audit_play_games.py` deliberately performs no mutations. It fails if Play has a missing resource or an unexpected resource that does not match the Android client's checked-in configuration and archives a non-secret JSON report for release evidence.

## September 15, 2026 live audit

Workflow run `34927496940` successfully authenticated to the Play Games Services Publishing API and found:

- **19 achievements**, exactly matching all 19 resource IDs compiled into the Android client;
- achievement types: **6 STANDARD** and **13 INCREMENTAL**, matching the Android behavior contract;
- **17 leaderboards**, exactly matching the Daily leaderboard plus all sixteen category leaderboard resource IDs compiled into the Android client;
- no missing or unexpected achievement IDs;
- no missing or unexpected leaderboard IDs.

Publication metadata is equally important:

- all **19 achievements are draft-only**;
- achievement published metadata count: **0**;
- all **17 leaderboards are draft-only**;
- leaderboard published metadata count: **0**.

That confirms the configured achievement/leaderboard resources have not yet been published for general Play Games users. They are currently suitable only for the project's enabled PGS testing access until the game configuration is explicitly published.

Google treats publishing Play Games Services configuration separately from publishing the Android app itself. Once runtime testing is complete, use the Play Games Services **Publishing** screen in Play Console to publish the game configuration. Google documents that PGS publication can take up to two hours to propagate, so publish the PGS configuration before the Android production release is made generally available.

References:

- https://developer.android.com/games/pgs/console/publish
- https://developer.android.com/games/pgs/publishing/publishing

## Saved Games

The Android client already contains conflict-safe Saved Games synchronization and uses snapshot name:

`eztrivia-player-state-v1`

The current Play Games Services Publishing API exposes achievement and leaderboard configuration resources, but it does **not** expose the Saved Games on/off project setting. The audit therefore does not infer Saved Games state.

Before production:

1. In Play Console → Play Games Services → Setup and management → Configuration, confirm **Saved Games** is turned on.
2. Install the Play-delivered Internal Testing build on two Android devices signed into the same Play Games profile.
3. Sync both devices once, take both offline, and make different progress on each.
4. Reconnect device A and then device B to force a real cloud conflict/merge.
5. Re-open both and verify lifetime points, round counters, histories, question progress, Daily/Friend one-attempt results, and cleared-history timestamps converge without loss or duplicate counting.

Do not publish the PGS configuration or advertise cross-device progress as production-ready until this test passes.

## Release order

The intended remaining PGS order is:

1. Confirm Saved Games is enabled.
2. Complete real-device Internal Testing for authentication, achievements, leaderboards, and two-device Saved Games conflict/merge behavior.
3. Re-run **Google Play Games configuration audit** and confirm all 19 achievement / 17 leaderboard IDs still exactly match the Android client.
4. Publish the Play Games Services game configuration in Play Console.
5. Allow propagation time before opening the Android Production release to general users.

Publishing PGS configuration is separate from moving the Android AAB from Internal to Production; do not confuse the two release operations.
