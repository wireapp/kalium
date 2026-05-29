#!/usr/bin/env bash
#
# Generates a customer-facing, file-level SBOM for the Kalium SDK.
#
# Pipeline:
#   1. Gradle materialises every third-party runtime artifact (jars, AARs, klibs,
#      resolved node_modules, native AVS libs) into build/sbom/artifacts/, and
#      every external dependency's Maven POM into build/sbom/poms/.
#   2. extractcode unpacks all archives recursively in place (this is what makes
#      AAR contents — classes.jar, AndroidManifest.xml, META-INF/ — visible to
#      ScanCode rather than being treated as opaque blobs).
#   3. scancode walks the unpacked tree and emits JSON, SPDX, CycloneDX, and
#      HTML reports under build/sbom/.
#   4. POM <licenses> blocks are parsed into scan-pom-licenses.tsv — the
#      authoritative metadata source for packages whose distribution doesn't
#      bundle a LICENSE file (kotlin-stdlib, atomicfu, kermit, AndroidX, etc.).
#   5. THIRD-PARTY-NOTICE.md is assembled: verbatim text from each bundled
#      LICENSE file plus a canonical-text appendix sourced from
#      ScanCode-Toolkit's licensedcode database (covers the SPDX long tail).
#
# Requirements:
#   - JDK 21 and the project's usual Gradle toolchain
#   - For iOS/macOS native deps: run on an Apple Silicon Mac. On Linux/Intel
#     hosts those targets resolve lenient-empty and the SBOM will omit them.
#
# Tunables (env vars):
#   SCANCODE_PROCESSES     parallel ScanCode workers (default: 8)
#   SBOM_GRADLE_EXTRA_ARGS extra Gradle args (e.g. "-PUSE_UNIFIED_CORE_CRYPTO=true")
#

set -euo pipefail

cd "$(dirname "$0")/.."

ARTIFACTS_DIR="build/sbom/artifacts"
OUTPUT_DIR="build/sbom"
SBOM_REQUIREMENTS_FILE="scripts/sbom-requirements.txt"
SCANCODE_PROCESSES="${SCANCODE_PROCESSES:-8}"
SBOM_GRADLE_EXTRA_ARGS="${SBOM_GRADLE_EXTRA_ARGS:-}"
SKIP_EXTRACT=false
SKIP_SCAN=false

require_cmd() {
    # Abort with a helpful message if $1 is not on PATH. $2 is an optional
    # hint string (e.g. "Install ScanCode-Toolkit.").
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERROR: '$1' not found on PATH. ${2:-Install it.}" >&2
        exit 1
    fi
}

usage() {
    cat >&2 <<EOF
Usage: $(basename "$0") [--skip-extract] [--skip-scan] [-h|--help]

  --skip-extract   Reuse an existing $ARTIFACTS_DIR tree: skip Gradle artifact
                   collection and extractcode unpacking, run scancode directly.
                   Use this to re-scan after tweaking scancode flags.
  --skip-scan      Reuse an existing $OUTPUT_DIR/scan.json: skip Gradle,
                   extractcode, scancode, and the venv/toolchain setup.
                   Only the post-scan license summaries are regenerated.
                   Implies --skip-extract.
  -h, --help       Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-extract) SKIP_EXTRACT=true; shift ;;
        --skip-scan) SKIP_SCAN=true; SKIP_EXTRACT=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "ERROR: unknown argument: $1" >&2; usage; exit 1 ;;
    esac
done

if [[ "$SKIP_EXTRACT" == "true" && "$SKIP_SCAN" != "true" && ! -d "$ARTIFACTS_DIR" ]]; then
    echo "ERROR: --skip-extract requires an existing $ARTIFACTS_DIR (none found)." >&2
    exit 1
fi

if [[ "$SKIP_SCAN" == "true" && ! -f "$OUTPUT_DIR/scan.json" ]]; then
    echo "ERROR: --skip-scan requires an existing $OUTPUT_DIR/scan.json (none found)." >&2
    exit 1
fi

# The :core:cryptography commonMain source set references the unified CoreCrypto
# KMP library, which is only wired in when USE_UNIFIED_CORE_CRYPTO=true. On macOS
# the iOS/macOS targets are also resolvable, so default the flag on there unless
# the caller already supplied it.
if [[ "$(uname -s)" == "Darwin" && "$SBOM_GRADLE_EXTRA_ARGS" != *"USE_UNIFIED_CORE_CRYPTO"* ]]; then
    SBOM_GRADLE_EXTRA_ARGS="-PUSE_UNIFIED_CORE_CRYPTO=true $SBOM_GRADLE_EXTRA_ARGS"
fi

if [[ "$SKIP_SCAN" == "true" ]]; then
    echo "==> Skipping toolchain setup and scancode (--skip-scan); reusing $OUTPUT_DIR/scan.json"
else

echo "==> [1/4] Setting up VENV and ScanCode..."
if [ ! -d ".venv" ]; then
    python3 -m venv .venv
fi
source .venv/bin/activate
echo

# scancode-toolkit transitively builds PyICU, which needs pkg-config + icu4c
# on macOS. Install them via Homebrew if missing and expose icu4c on PATH /
# PKG_CONFIG_PATH so the pip build can find it.
if [[ "$(uname -s)" == "Darwin" ]]; then
    require_cmd brew "Install Homebrew (required on macOS for pkg-config, icu4c, and libmagic)."
    for pkg in pkg-config icu4c; do
        if ! brew list --formula "$pkg" >/dev/null 2>&1; then
            echo "  Installing $pkg via Homebrew..."
            brew install "$pkg"
        fi
    done
    BREW_PREFIX="$(brew --prefix)"
    export PATH="$BREW_PREFIX/opt/icu4c/bin:$BREW_PREFIX/opt/icu4c/sbin:$PATH"
    export PKG_CONFIG_PATH="$BREW_PREFIX/opt/icu4c/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
fi

if [[ ! -f "$SBOM_REQUIREMENTS_FILE" ]]; then
    echo "ERROR: missing $SBOM_REQUIREMENTS_FILE." >&2
    exit 1
fi

# Always reconcile the venv to the pinned SBOM toolchain so CI and developer
# machines use the same ScanCode stack even when .venv already exists.
echo "  Installing pinned SBOM Python toolchain from $SBOM_REQUIREMENTS_FILE..."
python -m pip install --upgrade "pip==26.0.1" >/dev/null
python -m pip install --upgrade --requirement "$SBOM_REQUIREMENTS_FILE" >/dev/null

require_cmd extractcode "Install ScanCode-Toolkit."
require_cmd scancode "Install ScanCode-Toolkit."

# ScanCode's typecode module needs the libmagic C library + magic DB. On macOS
# these aren't bundled, so locate the Homebrew install and point typecode at it.
if [[ "$(uname -s)" == "Darwin" && -z "${TYPECODE_LIBMAGIC_PATH:-}" ]]; then
    # brew already validated above by the earlier Darwin block.
    if ! LIBMAGIC_PREFIX="$(brew --prefix libmagic 2>/dev/null)" || [[ -z "$LIBMAGIC_PREFIX" ]]; then
        echo "ERROR: libmagic not installed. Run 'brew install libmagic'." >&2
        exit 1
    fi
    LIBMAGIC_DYLIB="$LIBMAGIC_PREFIX/lib/libmagic.dylib"
    LIBMAGIC_DB="$LIBMAGIC_PREFIX/share/misc/magic.mgc"
    if [[ ! -f "$LIBMAGIC_DYLIB" || ! -f "$LIBMAGIC_DB" ]]; then
        echo "ERROR: libmagic files missing under $LIBMAGIC_PREFIX. Try 'brew reinstall libmagic'." >&2
        exit 1
    fi
    export TYPECODE_LIBMAGIC_PATH="$LIBMAGIC_DYLIB"
    export TYPECODE_LIBMAGIC_DB_PATH="$LIBMAGIC_DB"
    echo "  Using libmagic from $LIBMAGIC_PREFIX"
fi

if [[ "$SKIP_EXTRACT" == "true" ]]; then
    echo "==> [2/4] Skipping Gradle artifact collection (--skip-extract)"
    echo "==> [3/4] Skipping extractcode (--skip-extract); reusing $ARTIFACTS_DIR"
else
    echo "==> [2/4] Resolving and copying dependency artifacts via Gradle"
    # shellcheck disable=SC2086
    ./gradlew :collectSbomArtifacts $SBOM_GRADLE_EXTRA_ARGS

    if [[ ! -d "$ARTIFACTS_DIR" ]]; then
        echo "ERROR: $ARTIFACTS_DIR was not produced by Gradle." >&2
        exit 1
    fi

    echo "==> [3/4] Unpacking archives with extractcode (recursive)"
    # Recursive (no --shallow) so nested archives like classes.jar inside an
    # extracted .aar land on disk as plain files. ScanCode then skips the
    # original archive blobs via --ignore and never has to peek inside them
    # for package metadata, which is what inflated the scan count.
    extractcode "$ARTIFACTS_DIR"
fi

echo "==> [4/4] Running scancode (this will take 30+ minutes)"

# Glob patterns scancode should skip outright. These files carry no usable
# licence/copyright text — they're compiled bytecode, native binaries, original
# archives already unpacked by extractcode, or opaque media. Skipping them
# avoids hundreds of thousands of useless file scans.
SCANCODE_IGNORES=(
    # JVM / Kotlin / Android compiled bytecode
    '*.class' '*.dex' '*.kotlin_module' '*.kotlin_builtins' '*.kotlin_metadata'
    # Kotlin/Native compiled metadata inside .klib-extract/*/linkdata trees —
    # dominant share of the scan queue (~80k files in a full Kalium build),
    # all opaque binary blobs with no readable licence text.
    '*.knm' '*.knt' '*.knd' '*.knf' '*.knb'
    # Compiled native libraries and object files
    '*.so' '*.dylib' '*.dll' '*.jnilib' '*.o' '*.a' '*.obj' '*.lib' '*.wasm'
    # Misnamed bytecode (some Android .bin entries are actually compiled
    # Java class files; the .class ignore above misses them by extension).
    '*.bin'
    # Original archives already extracted in place by extractcode
    '*.jar' '*.aar' '*.war' '*.klib' '*.zip' '*.tar' '*.tar.gz' '*.tgz'
    # Images / fonts / media — no licence content
    '*.png' '*.jpg' '*.jpeg' '*.gif' '*.webp' '*.ico' '*.bmp'
    '*.ttf' '*.otf' '*.woff' '*.woff2' '*.eot'
    '*.mp3' '*.mp4' '*.wav' '*.ogg' '*.m4a'
    # JAR signature blocks
    '*.RSA' '*.SF' '*.DSA'
)

SCANCODE_ARGS=(
    --license --copyright --package --info --email --url
    --license-text --license-references
    --processes "$SCANCODE_PROCESSES"
    --json-pp "$OUTPUT_DIR/scan.json"
    --spdx-tv "$OUTPUT_DIR/scan.spdx"
)
for pattern in "${SCANCODE_IGNORES[@]}"; do
    SCANCODE_ARGS+=(--ignore "$pattern")
done
# --cyclonedx is available in ScanCode-Toolkit >= 32; if not present, emit a
# warning and continue rather than fail the whole run.
if scancode --help 2>&1 | grep -q -- '--cyclonedx'; then
    SCANCODE_ARGS+=(--cyclonedx "$OUTPUT_DIR/scan.cdx.json")
else
    echo "WARN: installed scancode lacks --cyclonedx; converting JSON afterwards (or skip)." >&2
fi

# Pre-scan diagnostic: count total files on disk, files matching any ignore
# glob, and the top extensions of what's left. Lets us sanity-check the input
# size before scancode burns hours on it.
echo
echo "  Pre-scan summary for $ARTIFACTS_DIR:"
TOTAL_FILES=$(find "$ARTIFACTS_DIR" -type f | wc -l | tr -d ' ')
IGNORE_FIND_EXPR=()
for pattern in "${SCANCODE_IGNORES[@]}"; do
    IGNORE_FIND_EXPR+=(-iname "$pattern" -o)
done
last_idx=$(( ${#IGNORE_FIND_EXPR[@]} - 1 ))
unset "IGNORE_FIND_EXPR[$last_idx]"  # drop trailing -o
IGNORED_FILES=$(find "$ARTIFACTS_DIR" -type f \( "${IGNORE_FIND_EXPR[@]}" \) | wc -l | tr -d ' ')
TO_SCAN=$((TOTAL_FILES - IGNORED_FILES))
printf "    total files on disk: %d\n" "$TOTAL_FILES"
printf "    matched by --ignore: %d\n" "$IGNORED_FILES"
printf "    will be scanned:     %d\n" "$TO_SCAN"
echo "    top 10 extensions in the to-be-scanned set:"
find "$ARTIFACTS_DIR" -type f ! \( "${IGNORE_FIND_EXPR[@]}" \) \
    | sed -n 's/.*\.\([A-Za-z0-9]*\)$/\1/p' \
    | sort | uniq -c | sort -rn | head -10 \
    | awk '{printf "      %6d  .%s\n", $1, $2}'
echo

scancode "${SCANCODE_ARGS[@]}" "$ARTIFACTS_DIR"

fi  # end --skip-scan branch

# Plain-text license summary derived from scan.json. The bundled --html-app
# viewer struggles with large data.js files; this gives a quick human-readable
# rollup of license_expression -> file count without involving a browser.
echo
echo "==> Generating license summary from scan.json"
if command -v jq >/dev/null 2>&1; then
    # Rollup: license_expression -> file count, sorted descending.
    jq -r '
      .files
      | map(select(.detected_license_expression != null))
      | group_by(.detected_license_expression)
      | map({license: .[0].detected_license_expression, files: length})
      | sort_by(-.files)[]
      | "\(.files)\t\(.license)"
    ' "$OUTPUT_DIR/scan.json" > "$OUTPUT_DIR/scan-licenses.tsv"
    LICENSE_COUNT=$(wc -l < "$OUTPUT_DIR/scan-licenses.tsv" | tr -d ' ')
    echo "  Wrote $OUTPUT_DIR/scan-licenses.tsv ($LICENSE_COUNT distinct license expressions)"

    # Detail: per license, list every file that carries it.
    jq -r '
      .files
      | map(select(.detected_license_expression != null))
      | group_by(.detected_license_expression)[]
      | "## \(.[0].detected_license_expression) — \(length) files",
        (.[].path | "  \(.)"),
        ""
    ' "$OUTPUT_DIR/scan.json" > "$OUTPUT_DIR/scan-licenses-by-file.txt"
    echo "  Wrote $OUTPUT_DIR/scan-licenses-by-file.txt"

    # Detail: per license, list every unpacked third-party package that carries it.
    # Collapses many files inside e.g. slf4j-api-2.0.17.jar-extract/ down to that
    # one package directory; falls back to the first three path segments for
    # paths that aren't inside an extracted archive (npm modules, raw natives
    # like artifacts/native/avs/...). Using scan (not capture) here so paths
    # with no archive segment yield an empty stream instead of an error.
    jq -r '
      .files
      | map(select(.detected_license_expression != null))
      | map({
          license: .detected_license_expression,
          pkg: ([.path | scan("[^/]+\\.(?:jar|aar|klib|war|zip)-extract")][0]
                // (.path | split("/")[0:3] | join("/")))
        })
      | unique_by({license, pkg})
      | group_by(.license)[]
      | "## \(.[0].license) — \(length) packages",
        (.[].pkg | "  \(.)"),
        ""
    ' "$OUTPUT_DIR/scan.json" > "$OUTPUT_DIR/scan-licenses-by-package.txt"
    echo "  Wrote $OUTPUT_DIR/scan-licenses-by-package.txt"
else
    echo "WARN: jq not found on PATH; skipping license summary. Install jq to enable." >&2
fi

# License-file presence audit. Independent of scancode: walks the unpacked
# artifact tree directly to flag third-party packages that do NOT ship a
# LICENSE / NOTICE / COPYING file alongside their code. The missing list is
# the punch list for the customer THIRD-PARTY-NOTICE.md — every entry needs
# a manual fallback (POM <licenses>, upstream repo, etc.) before the
# deliverable is complete.
#
# Dedup matters here: the same third-party jar (e.g. kotlin-stdlib-2.3.20.jar)
# is resolved by every Kalium module that uses Kotlin and therefore appears
# under many per-module paths in build/sbom/artifacts/. We collapse those to
# one entry per unique package — basename for *-extract dirs, name (or
# @scope/name) for npm packages, and a single "native/avs" entry for the
# prebuilt AVS blob.
echo
echo "==> Auditing for LICENSE/NOTICE/COPYING files in each unique third-party package"
if [[ -d "$ARTIFACTS_DIR" ]]; then
    require_cmd python3 "python3 is required for the license-file audit."
    python3 scripts/audit-license-files.py "$ARTIFACTS_DIR" "$OUTPUT_DIR"
else
    echo "  Skipped (no $ARTIFACTS_DIR on disk)"
fi

# POM-derived license metadata. Every Maven POM materialised by Sbom.kt is
# parsed for its <licenses> block — the authoritative SPDX-equivalent source
# for license name + URL per package. This fills the gap for packages that
# don't bundle a LICENSE file in their jar/aar/klib and is the structured
# feedstock for the eventual customer THIRD-PARTY-NOTICE.md.
echo
echo "==> Parsing POM <licenses> metadata"
POMS_DIR="$OUTPUT_DIR/poms"
# Prefer the venv python — it has PyYAML installed transitively via
# scancode-toolkit, which the override-loader needs. Fall back to a bare
# python3 only if a yaml module is importable there too, otherwise skip
# (rather than dying mid-pipeline on `import yaml`).
POM_PY=""
if [[ -x .venv/bin/python3 ]] && .venv/bin/python3 -c 'import yaml' 2>/dev/null; then
    POM_PY=".venv/bin/python3"
elif command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' 2>/dev/null; then
    POM_PY="python3"
fi
if [[ ! -d "$POMS_DIR" ]]; then
    echo "  Skipped (no $POMS_DIR — run Gradle artifact collection to materialise POMs)"
elif [[ -z "$POM_PY" ]]; then
    echo "  Skipped (no python3 with PyYAML available — run the full pipeline once to provision .venv)"
else
    POM_TSV="$OUTPUT_DIR/scan-pom-licenses.tsv"
    POM_NOLIC="$OUTPUT_DIR/scan-pom-no-licenses.txt"
    OVERRIDE_YAML="scripts/sbom-license-overrides.yaml"
    "$POM_PY" scripts/parse-pom-licenses.py "$POMS_DIR" "$POM_TSV" "$POM_NOLIC" "$OVERRIDE_YAML"
fi

# Customer-facing THIRD-PARTY-NOTICE.md. Concatenates per-package verbatim
# LICENSE/NOTICE text from scan-license-files-found.txt and appends canonical
# SPDX license text for every license referenced by the POM metadata. Long-tail
# coverage comes for free: the appendix pulls from ScanCode-Toolkit's bundled
# licensedcode database (~2000 licenses) so new dependencies with exotic
# licenses don't need a code change.
echo
echo "==> Generating THIRD-PARTY-NOTICE.md"
NOTICE_PY=""
if [[ -x .venv/bin/python3 ]]; then
    NOTICE_PY=".venv/bin/python3"
fi
if [[ -z "$NOTICE_PY" ]]; then
    echo "  Skipped (no .venv/bin/python3 — run the full pipeline once to install scancode)"
elif ! "$NOTICE_PY" -c 'from licensedcode.cache import get_licenses_db' 2>/dev/null; then
    echo "  Skipped (scancode not importable from .venv — try 'pip install scancode-toolkit')"
elif [[ ! -f "$OUTPUT_DIR/scan-pom-licenses.tsv" || ! -f "$OUTPUT_DIR/scan-license-files-found.txt" ]]; then
    echo "  Skipped (missing upstream outputs in $OUTPUT_DIR — run earlier steps first)"
else
    "$NOTICE_PY" scripts/generate-third-party-notice.py "$OUTPUT_DIR" THIRD-PARTY-NOTICE.md
fi

# Customer SBOM bundle: pack the four canonical deliverable files into a
# single archive next to the scan outputs. `zip -j` flattens the structure
# so the recipient sees a flat list of files instead of build/sbom/... paths.
echo
echo "==> Bundling customer SBOM archive"
SBOM_BUNDLE="$OUTPUT_DIR/SBOM-and-license.zip"
BUNDLE_INPUTS=(
    "$OUTPUT_DIR/scan.json"
    "$OUTPUT_DIR/scan.cdx.json"
    "$OUTPUT_DIR/scan.spdx"
    "THIRD-PARTY-NOTICE.md"
)
EXISTING_INPUTS=()
for f in "${BUNDLE_INPUTS[@]}"; do
    if [[ -f "$f" ]]; then
        EXISTING_INPUTS+=("$f")
    else
        echo "  WARN: missing input $f — skipping" >&2
    fi
done
if [[ ${#EXISTING_INPUTS[@]} -eq 0 ]]; then
    echo "  ERROR: no input files found, skipping bundle." >&2
elif ! command -v zip >/dev/null 2>&1; then
    echo "  WARN: 'zip' not on PATH — skipping bundle. Install zip to enable." >&2
else
    rm -f "$SBOM_BUNDLE"
    zip -j "$SBOM_BUNDLE" "${EXISTING_INPUTS[@]}" >/dev/null
    echo "  Wrote $SBOM_BUNDLE (${#EXISTING_INPUTS[@]} files)"
fi

echo
echo "SBOM outputs:"
ls -lh "$OUTPUT_DIR"/scan.* \
       "$OUTPUT_DIR"/scan-summary.html \
       "$OUTPUT_DIR"/scan-licenses.tsv \
       "$OUTPUT_DIR"/scan-licenses-by-file.txt \
       "$OUTPUT_DIR"/scan-licenses-by-package.txt \
       "$OUTPUT_DIR"/scan-license-files-found.txt \
       "$OUTPUT_DIR"/scan-license-files-missing.txt \
       "$OUTPUT_DIR"/scan-pom-licenses.tsv \
       "$OUTPUT_DIR"/scan-pom-no-licenses.txt \
       "$OUTPUT_DIR"/SBOM-and-license.zip 2>/dev/null || true
ls -lh THIRD-PARTY-NOTICE.md 2>/dev/null || true
