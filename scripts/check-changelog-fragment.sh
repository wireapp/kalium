#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${BASE_REF:-}"
HEAD_REF="${HEAD_REF:-HEAD}"

usage() {
  cat <<'USAGE'
Usage: BASE_REF=<base> [HEAD_REF=<head>] scripts/check-changelog-fragment.sh

Requires a changelog fragment when ABI dump files changed or the pull request has
an API-impacting label. The labels are read from GITHUB_EVENT_PATH when available.
Skip labels:
  - no-changelog-needed
  - internal-only
API labels:
  - api-impacting
  - api-impacting change
  - api-impacting-change
  - public-api
  - 🚨 Potential breaking changes
USAGE
}

if [[ "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "$BASE_REF" ]]; then
  echo "BASE_REF must be set to the branch, tag, or commit to compare against." >&2
  exit 2
fi

if ! git cat-file -e "$BASE_REF^{commit}"; then
  echo "BASE_REF '$BASE_REF' does not resolve to a commit." >&2
  exit 2
fi

if ! git cat-file -e "$HEAD_REF^{commit}"; then
  echo "HEAD_REF '$HEAD_REF' does not resolve to a commit." >&2
  exit 2
fi

changed_files="$(
  git diff --name-only "$BASE_REF...$HEAD_REF"
  # Include untracked files so local pre-commit runs see new fragments and ABI dumps.
  git ls-files --others --exclude-standard
)"

added_or_modified_files="$(
  git diff --name-only --diff-filter=AM "$BASE_REF...$HEAD_REF"
  # Include untracked files so local pre-commit runs see new fragments and ABI dumps.
  git ls-files --others --exclude-standard
)"

has_changed_file() {
  local pattern="$1"
  grep -Eq "$pattern" <<< "$changed_files"
}

has_added_or_modified_file() {
  local pattern="$1"
  grep -Eq "$pattern" <<< "$added_or_modified_files"
}

event_has_label() {
  local label="$1"
  local event_path="${GITHUB_EVENT_PATH:-}"

  [[ -n "$event_path" && -f "$event_path" ]] || return 1

  EVENT_PATH="$event_path" LABEL_NAME="$label" python3 - <<'PY'
import json
import os

with open(os.environ["EVENT_PATH"], encoding="utf-8") as event_file:
    event = json.load(event_file)

labels = event.get("pull_request", {}).get("labels", [])
names = {label.get("name", "") for label in labels}
raise SystemExit(0 if os.environ["LABEL_NAME"] in names else 1)
PY
}

has_changelog_fragment=false
fragment_files="$(
  grep -E '^changelog\.d/[^/]+\.md$' <<< "$added_or_modified_files" |
    grep -v '^changelog\.d/README\.md$' || true
)"
if [[ -n "$fragment_files" ]]; then
  has_changelog_fragment=true
fi

abi_changed=false
if has_changed_file '(^|/)api/.*\.(api|klib\.api)$'; then
  abi_changed=true
fi

api_label=false
if event_has_label "api-impacting" ||
  event_has_label "api-impacting change" ||
  event_has_label "api-impacting-change" ||
  event_has_label "public-api" ||
  event_has_label "🚨 Potential breaking changes"; then
  api_label=true
fi

no_changelog_needed=false
if event_has_label "no-changelog-needed"; then
  no_changelog_needed=true
fi

internal_only=false
if event_has_label "internal-only"; then
  internal_only=true
fi

if [[ "$has_changelog_fragment" == true || "$no_changelog_needed" == true ]]; then
  echo "Changelog gate passed."
  exit 0
fi

if [[ "$internal_only" == true && "$abi_changed" == false ]]; then
  echo "Changelog gate passed for internal-only change with no ABI dump changes."
  exit 0
fi

if [[ "$abi_changed" == true || "$api_label" == true ]]; then
  cat >&2 <<'ERROR'
This change appears to affect public API/ABI but does not include a changelog fragment.

Add a consumer-facing Markdown fragment under changelog.d/, or apply one of these
reviewer-accepted labels when appropriate:
  - no-changelog-needed
  - internal-only
ERROR
  exit 1
fi

echo "No public API/ABI changelog requirement detected."
