# Notes for Claude

- The owner does NOT merge pull requests. Claude merges its own PRs (squash) once CI is green, then continues the release flow.
- Develop on the `claude/elegant-allen-ml1shu` branch; open PRs as drafts, mark ready and merge after checks pass.
- Star/Run releases: edit `.github/starrun-release-request.json` (TestFlight) and `.github/starrun-android-release-request.json` (Google Play; keep `release_status` as `draft` unless the owner asks for a release).
