# Android parity release — iOS 1.4 baseline

**Status:** implemented on feature branch; do not roll out until CI, Play Games, and installed-device checks pass.

## Changes in this Android-only update

1. Play home is a single scroll surface: **Daily Challenge → Quick Play → Friend Challenge → categories**. Hero cards re-use the existing Daily and Friend activities and question algorithms.
2. Bottom navigation is **Play / Scores / Settings** like iOS. Daily/Friend remain accessible from Play, including the existing incoming Friend deep links.
3. Quick Play and Daily use the purple/indigo and orange/pink action gradients from iOS. Category cards show distinct symbols and the **best percentage for Easy / Medium / Hard**, based on the most recent 50 category rounds (same source of truth and reset behavior as iOS). The Scores tab retains coverage and achievements.
4. All four round paths (category, Quick Play, Daily, Friend) offer **Report this question** after answering. The user controls a prefilled email that includes the stable question ID, selected/correct answers, category, difficulty, explanation, and app version.
5. A completed Daily offers **View Daily leaderboard on Play Games**. The button attempts platform sign-in and submits only a current-day score before opening the native Daily board.

No question content, deterministic Daily/Friend selection, scoring, persistence schema, Android ad placements, billing, or iOS source is changed.

## Verification

- [ ] Android CI: unit tests, lint, debug/release builds, R8 packaging and AAB
- [ ] Emulator/installed APK: home is fully scrollable on a small phone and with large fonts
- [ ] Play tab: Daily opens today's challenge; Quick Play opens a mixed round; Friend opens the lobby; tapping a category opens Easy/Medium/Hard
- [ ] Play/Scores/Settings navigation; in-round bottom bar and home banners do not appear
- [ ] Easy/Medium/Hard best scores display, update after rounds, and clear with recent history
- [ ] After a correct or wrong answer in every mode, the report action opens a prefilled email; no email is sent without the player's action
- [ ] Daily leaderboard button handles signed-in and signed-out states, without replaying a prior day's result into today's scores
- [ ] AdMob and Remove Ads still behave correctly on the Play home (production serving needs verified AdMob request/impression data, not just source checks)
- [ ] Google Play Games Daily leaderboard is published/visible to the installed app and its configured tester/player account
- [ ] Verify currently installed Android version/track, assign the **next** versionCode, and choose release track intentionally. Do not replace the existing closed test or publish automatically.

## Remaining differences / not in this release

- iOS VoiceOver auto-advance protection remains an iOS-only accessibility follow-up.
- Android opens **native Play Games Daily standings**, whereas iOS 1.4 displays global/friends ranking information inline when Game Center returns it. Full rank-card parity would require a separate authenticated Android leaderboard data implementation.
- Android offers an explicit dark-mode setting; iOS follows the system preference. Both support dark appearance.
- AdMob live delivery and Play Games publication cannot be established through source code alone.

This document describes the feature branch and is not evidence of a published Google Play release.
