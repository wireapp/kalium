#!/usr/bin/env bash
# Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

enabled=false
read_only=false

# Only develop pushes, same-repository PRs to develop, and its merge queue use caches.
if [[ "${EVENT_NAME-}" == "push" && "${REF-}" == "refs/heads/develop" ]]; then
  enabled=true
elif [[ "${EVENT_NAME-}" == "pull_request" &&
        "${PR_BASE_REF-}" == "develop" &&
        -n "${REPOSITORY-}" && "${PR_HEAD_REPOSITORY-}" == "${REPOSITORY}" ]]; then
  enabled=true
elif [[ "${EVENT_NAME-}" == "merge_group" &&
        "${MERGE_GROUP_BASE_REF-}" == "refs/heads/develop" ]]; then
  enabled=true
  read_only=true
fi

if [[ "${GRADLE_OPTS-}" == *$'\n'* || "${GRADLE_OPTS-}" == *$'\r'* ]]; then
  echo "::error::GRADLE_OPTS must not contain line breaks"
  exit 1
fi

printf 'enabled=%s\nread_only=%s\n' "$enabled" "$read_only" >> "$GITHUB_OUTPUT"
# Override the repository's local default and preserve the decision in nested builds.
gradle_opts="${GRADLE_OPTS:+${GRADLE_OPTS} }-Dorg.gradle.caching=${enabled}"
printf 'GRADLE_OPTS=%s\n' "$gradle_opts" >> "$GITHUB_ENV"
