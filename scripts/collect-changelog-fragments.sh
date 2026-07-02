#!/usr/bin/env bash
set -euo pipefail

CHANGELOG_PATH="${CHANGELOG_PATH:-CHANGELOG.md}"
FRAGMENT_DIR="${FRAGMENT_DIR:-changelog.d}"
BASE_REF="${BASE_REF:-}"
HEAD_REF="${HEAD_REF:-HEAD}"

if [[ ! -d "$FRAGMENT_DIR" ]]; then
  exit 0
fi

fragments=()
if [[ -n "$BASE_REF" ]]; then
  while IFS= read -r fragment; do
    case "$fragment" in
      "$FRAGMENT_DIR"/*.md)
        [[ "$fragment" == "$FRAGMENT_DIR/README.md" ]] && continue
        [[ -f "$fragment" ]] || continue
        fragments+=("$fragment")
        ;;
    esac
  done < <(git diff --name-only --diff-filter=AM "$BASE_REF" "$HEAD_REF" -- "$FRAGMENT_DIR" | sort)
else
  while IFS= read -r fragment; do
    fragments+=("$fragment")
  done < <(find "$FRAGMENT_DIR" -maxdepth 1 -type f -name '*.md' ! -name 'README.md' | sort)
fi

if [[ "${#fragments[@]}" -eq 0 ]]; then
  exit 0
fi

tmp_file="$(mktemp)"
{
  echo "# Consumer-facing changes"
  echo
  for fragment in "${fragments[@]}"; do
    cat "$fragment"
    echo
  done
  if [[ -f "$CHANGELOG_PATH" ]]; then
    echo
    cat "$CHANGELOG_PATH"
  fi
} > "$tmp_file"

mv "$tmp_file" "$CHANGELOG_PATH"
