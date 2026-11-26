#!/usr/bin/env python3
"""Public, read-only release evidence. Never dispatches, builds or publishes."""
import argparse
import datetime as dt
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

REGISTRY = Path(__file__).with_name("apps.json")
SHA = re.compile(r"[0-9a-f]{40}")
VERSION = re.compile(r"[0-9]+(?:\.[0-9]+){0,3}")
REPO = re.compile(r"killjoy00/[A-Za-z0-9_.-]+")
WORKFLOW = re.compile(r"[A-Za-z0-9_.-]+\.ya?ml")
BUNDLE = re.compile(r"[A-Za-z0-9_.-]+")
BRANCH = re.compile(r"[A-Za-z0-9_.-]+")
UNKNOWN = "Not verified"

def valid(pattern, value):
    return isinstance(value, str) and pattern.fullmatch(value) is not None

def read_json(url):
    # No credentials or write methods. Never print response bodies or exceptions.
    req = urllib.request.Request(url, headers={
        "User-Agent": "mobile-release-status-pilot/1",
        "Accept": "application/json",
    }, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=12) as response:
            return json.loads(response.read(2_000_000))
    except (OSError, ValueError):
        return None

def validate_registry(registry):
    assert registry["schema_version"] == 1
    seen = set()
    for app in registry["apps"]:
        assert app["id"] not in seen
        seen.add(app["id"])
        assert valid(REPO, app["source"])
        for platform in ("ios", "android"):
            route = app[platform]
            if "workflow" in route:
                assert valid(REPO, route["repo"])
                assert valid(WORKFLOW, route["workflow"])
                assert valid(BRANCH, route.get("branch", "main"))
            if "bundle" in route:
                assert platform == "ios" and valid(BUNDLE, route["bundle"])
    return registry["apps"]

def head(repo, fetch):
    data = fetch(f"https://api.github.com/repos/{repo}/commits/main")
    sha = data.get("sha") if isinstance(data, dict) else None
    return f"[{sha[:8]}](https://github.com/{repo}/commit/{sha})" if valid(SHA, sha) else UNKNOWN

def workflow_run(route, fetch):
    if "workflow" not in route:
        return "Route not verified"
    repo, workflow = route["repo"], route["workflow"]
    query = urllib.parse.urlencode({"branch": route.get("branch", "main"), "per_page": 1})
    data = fetch(f"https://api.github.com/repos/{repo}/actions/workflows/{workflow}/runs?{query}")
    runs = data.get("workflow_runs") if isinstance(data, dict) else None
    if not runs or not isinstance(runs, list) or not isinstance(runs[0], dict):
        return UNKNOWN
    run = runs[0]
    run_id = run.get("id")
    if type(run_id) is not int or run_id <= 0:
        return UNKNOWN
    state = run.get("conclusion") or run.get("status")
    allowed = {"success", "failure", "cancelled", "skipped", "timed_out",
               "action_required", "neutral", "queued", "in_progress", "waiting",
               "requested", "pending", "completed", "stale", "startup_failure"}
    state = state if isinstance(state, str) and state in allowed else "unknown"
    return f"[{state}](https://github.com/{repo}/actions/runs/{run_id})"

def apple_public(route, fetch):
    bundle = route.get("bundle")
    if not bundle:
        return "Identity not verified"
    query = urllib.parse.urlencode({"bundleId": bundle, "country": "us", "entity": "software"})
    data = fetch(f"https://itunes.apple.com/lookup?{query}")
    results = data.get("results") if isinstance(data, dict) else None
    if not isinstance(results, list):
        return UNKNOWN
    matches = [r for r in results if isinstance(r, dict) and r.get("bundleId") == bundle]
    if len(matches) == 1 and valid(VERSION, matches[0].get("version")):
        return matches[0]["version"] + " (US listing)"
    # Absence is not proof of unpublished status.
    return UNKNOWN

def render(registry, fetch, now):
    apps = validate_registry(registry)
    heads = {repo: head(repo, fetch) for repo in dict.fromkeys(a["source"] for a in apps)}
    lines = ["# Mobile release status — read-only pilot", "",
             f"Checked at {now} UTC. A workflow result is NOT a signed build, upload, or store confirmation.", "",
             "| App | Development main | Platform | Latest configured release workflow | Signed / uploaded | Testing | Submitted | Public |",
             "|---|---|---|---|---|---|---|---|"]
    for app in apps:
        for platform in ("ios", "android"):
            route = app[platform]
            run = workflow_run(route, fetch)
            public = apple_public(route, fetch) if platform == "ios" else UNKNOWN
            lines.append(f"| {app['name']} | {heads[app['source']]} | {platform} | {run} | {UNKNOWN} | {UNKNOWN} | {UNKNOWN} | {public} |")
    lines += ["", "A shared publisher run's commit can belong to EZTrivia, not the app's binary.",
              "Development main is not the shipped commit. No authenticated store reads are wired in yet.",
              "Apple results describe only the US public listing; unknown does not mean unavailable.",
              "Failures and unavailable routes require investigation; do not infer release blockers from a workflow result alone."]
    return "\n".join(lines) + "\n"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true", help="Read public GitHub and Apple listing APIs")
    args = parser.parse_args()
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    now = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d %H:%M")
    print(render(registry, read_json if args.live else lambda _: None, now), end="")

if __name__ == "__main__":
    main()
