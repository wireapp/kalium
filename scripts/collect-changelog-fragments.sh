#!/usr/bin/env bash
set -euo pipefail

CHANGELOG_PATH="${CHANGELOG_PATH:-CHANGELOG.md}"
FRAGMENT_DIR="${FRAGMENT_DIR:-changelog.d}"
BASE_REF="${BASE_REF:-}"
HEAD_REF="${HEAD_REF:-HEAD}"
CURRENT_TAG="${CURRENT_TAG:-$HEAD_REF}"
TOWNCRIER_BIN="${TOWNCRIER_BIN:-towncrier}"
TOWNCRIER_CONFIG="${TOWNCRIER_CONFIG:-towncrier.toml}"

if [[ "$TOWNCRIER_CONFIG" != /* ]]; then
  TOWNCRIER_CONFIG="$PWD/$TOWNCRIER_CONFIG"
fi

if [[ ! -d "$FRAGMENT_DIR" ]]; then
  exit 0
fi

fragment_pattern="^$FRAGMENT_DIR/[^/]+\\.(added|changed|deprecated|removed|fixed|security|migration)\\.md$"
fragments=()
if [[ -n "$BASE_REF" ]]; then
  while IFS= read -r fragment; do
    [[ "$fragment" =~ $fragment_pattern ]] || continue
    [[ -f "$fragment" ]] || continue
    fragments+=("$fragment")
  done < <(
    {
      git diff --name-only --diff-filter=AM "$BASE_REF...$HEAD_REF" -- "$FRAGMENT_DIR"
      git ls-files --others --exclude-standard -- "$FRAGMENT_DIR"
    } | sort -u
  )
else
  while IFS= read -r fragment; do
    fragments+=("$fragment")
  done < <(
    find "$FRAGMENT_DIR" -maxdepth 1 -type f \
      \( -name '*.added.md' -o \
        -name '*.changed.md' -o \
        -name '*.deprecated.md' -o \
        -name '*.removed.md' -o \
        -name '*.fixed.md' -o \
        -name '*.security.md' -o \
        -name '*.migration.md' \) |
      sort
  )
fi

if [[ "${#fragments[@]}" -eq 0 ]]; then
  exit 0
fi

if ! command -v "$TOWNCRIER_BIN" >/dev/null 2>&1; then
  echo "towncrier is required. Install it with: python3 -m pip install --requirement scripts/changelog-requirements.txt" >&2
  exit 2
fi

tmp_dir="$(mktemp -d)"
tmp_file="$(mktemp)"
consumer_notes="$(mktemp)"
trap 'rm -rf "$tmp_dir" "$tmp_file" "$consumer_notes"' EXIT

mkdir -p "$tmp_dir/changelog.d"
for fragment in "${fragments[@]}"; do
  cp "$fragment" "$tmp_dir/changelog.d/"
done

"$TOWNCRIER_BIN" build \
  --config "$TOWNCRIER_CONFIG" \
  --dir "$tmp_dir" \
  --draft \
  --version "$CURRENT_TAG" > "$consumer_notes"

if [[ -f "$CHANGELOG_PATH" ]]; then
  {
    cat "$consumer_notes"
    echo
    cat "$CHANGELOG_PATH"
  } > "$tmp_file"
else
  cat "$consumer_notes" > "$tmp_file"
fi

mv "$tmp_file" "$CHANGELOG_PATH"
