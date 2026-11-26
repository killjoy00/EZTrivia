# Mobile release management — pilot

This is a portable read-only layer over the existing publishing workflows, tracked in [#190](https://github.com/killjoy00/EZTrivia/issues/190). The preferred permanent dashboard is a dedicated private repository; do not copy credentials or rewrite app publishing routes to move it.

## Dashboard

Open [Mobile release status](https://github.com/killjoy00/EZTrivia/actions/workflows/mobile-release-status.yml) and select the latest **main** run's summary. The pilot refreshes when these files merge or by manual dispatch. It does not schedule recurring jobs, upload artifacts, create release requests or contact authenticated store APIs. The public Apple lookup is an exact-bundle US listing check; Google Play and authenticated build/test/review fields stay unknown until verified. The workflow result is not a release confirmation. The source main SHA is not the signed artifact SHA. Rate limits and outages must not be interpreted as absence.

## Standard agent request

Accept natural language such as “Release Star/Run 1.3 to TestFlight and Google Play internal testing.” Resolve the app and platform in [apps.json](apps.json); read the current source/owner workflow and current request file before making changes. Normalize the intent:

- Application ID and platform(s); target separately for each platform (build only, private testing, submit for review, or production).
- Exact reviewed source commit SHA and requested marketing version for each platform; existing build/version allocator remains authoritative.
- Explicit owner authorization for each requested target; approval of a code PR or test upload is not permission for a production release.
- Current acceptance gates and exact artifact/build identity where applicable, especially production promotion.

Use the existing app-specific request-file PR or supported manual workflow only. Never fabricate a request or trigger a production promotion by guessing a version. For a route marked unverified or version-specific, inspect the current workflow and request first. If the connector cannot dispatch a manual-only workflow, report that limitation; do not invent an unsafe automatic trigger. Preserve Star/Run's draft default unless specifically authorized to distribute to internal testers. Follow Pack One's own runbook, gates and exact-artifact requirements. Use existing signing and production promotion paths unchanged.

Report source, signed build, upload, processing, testing track/group availability, review submission and public availability as separate stages with links, timestamps and evidence where available; state unknown where not verified. A workflow success or a store draft is never evidence of public availability. Sanitize public reports: no secrets, account identifiers, personal data, unreleased private operational detail, or raw store responses.

## Scope and rollout

All ten apps are registered, including two separate iOS identities sharing Starport's repo. EZTrivia and Star/Run are the first pilot; do not alter other app publishing infrastructure until the pilot is verified. Gold Rush's configured Apple bundle can be overridden, so its public identity is not assumed. Ultimate Tic/Tac/Toe's version-specific Android adapters need separate review. Food Finder and TurnPro are not assumed to have store-ready releases.

No new paid services or libraries. Run offline validation with `python3 -m unittest discover -s mobile-releases -p 'test_*.py'`. The dashboard workflow runs those tests before making a read-only refresh. Authenticated store read-back, credential health checks, production release routing and measured CI savings are follow-up work, not implemented by this pilot.
