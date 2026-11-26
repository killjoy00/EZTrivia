# Notes for Claude

- The owner does NOT merge pull requests. Claude merges its own PRs (squash) once CI is green, then continues the release flow.
- Develop on the `claude/elegant-allen-ml1shu` branch; open PRs as drafts, mark ready and merge after checks pass.
- Star/Run releases: edit `.github/starrun-release-request.json` (TestFlight) and `.github/starrun-android-release-request.json` (Google Play; keep `release_status` as `draft` unless the owner asks for a release).

## Portfolio mobile releases

The canonical app registry, read-only dashboard and standard release request protocol are in [mobilereleases](https://github.com/killjoy00/mobilereleases). Read its current README and registry before any release request. EZTrivia also hosts publishing workflows for other registered apps. Keep signing and release request files here; registry is not a publisher. Follow this repository's existing tests, release authorization and app-specific requirements. Never equate a successful workflow with store availability or publish to production without explicit owner approval. Report store stages separately and sanitize public evidence.
