#!/usr/bin/env bash
#
# Generates a customer-facing, file-level SBOM for the Kalium SDK.
#
# Pipeline:
#   1. Gradle materialises every third-party runtime artifact (jars, AARs, klibs,
#      resolved node_modules, native AVS libs) into build/sbom/artifacts/.
#   2. extractcode unpacks all archives recursively in place (this is what makes
#      AAR contents — classes.jar, AndroidManifest.xml, META-INF/ — visible to
#      ScanCode rather than being treated as opaque blobs).
#   3. scancode walks the unpacked tree and emits JSON, SPDX, CycloneDX, and
#      HTML reports under build/sbom/.
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
SCANCODE_PROCESSES="${SCANCODE_PROCESSES:-8}"
SBOM_GRADLE_EXTRA_ARGS="${SBOM_GRADLE_EXTRA_ARGS:-}"
SKIP_EXTRACT=false

usage() {
    cat >&2 <<EOF
Usage: $(basename "$0") [--skip-extract] [-h|--help]

  --skip-extract   Reuse an existing $ARTIFACTS_DIR tree: skip Gradle artifact
                   collection and extractcode unpacking, run scancode directly.
                   Use this to re-scan after tweaking scancode flags.
  -h, --help       Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-extract) SKIP_EXTRACT=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "ERROR: unknown argument: $1" >&2; usage; exit 1 ;;
    esac
done

if [[ "$SKIP_EXTRACT" == "true" && ! -d "$ARTIFACTS_DIR" ]]; then
    echo "ERROR: --skip-extract requires an existing $ARTIFACTS_DIR (none found)." >&2
    exit 1
fi

# The :core:cryptography commonMain source set references the unified CoreCrypto
# KMP library, which is only wired in when USE_UNIFIED_CORE_CRYPTO=true. On macOS
# the iOS/macOS targets are also resolvable, so default the flag on there unless
# the caller already supplied it.
if [[ "$(uname -s)" == "Darwin" && "$SBOM_GRADLE_EXTRA_ARGS" != *"USE_UNIFIED_CORE_CRYPTO"* ]]; then
    SBOM_GRADLE_EXTRA_ARGS="-PUSE_UNIFIED_CORE_CRYPTO=true $SBOM_GRADLE_EXTRA_ARGS"
fi

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
    if ! command -v brew >/dev/null 2>&1; then
        echo "ERROR: Homebrew is required on macOS to install pkg-config and icu4c." >&2
        exit 1
    fi
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

if ! command -v scancode >/dev/null 2>&1; then
    echo "  Installing scancode-toolkit..."
    pip install --upgrade pip setuptools wheel >/dev/null
    pip install scancode-toolkit >/dev/null
else
    echo "  scancode-toolkit already installed"
fi

command -v extractcode >/dev/null 2>&1 || {
    echo "ERROR: 'extractcode' not found on PATH. Install ScanCode-Toolkit." >&2
    exit 1
}
command -v scancode >/dev/null 2>&1 || {
    echo "ERROR: 'scancode' not found on PATH. Install ScanCode-Toolkit." >&2
    exit 1
}

# ScanCode's typecode module needs the libmagic C library + magic DB. On macOS
# these aren't bundled, so locate the Homebrew install and point typecode at it.
if [[ "$(uname -s)" == "Darwin" && -z "${TYPECODE_LIBMAGIC_PATH:-}" ]]; then
    if ! command -v brew >/dev/null 2>&1; then
        echo "ERROR: libmagic is required on macOS. Install Homebrew and run 'brew install libmagic'." >&2
        exit 1
    fi
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

    echo "==> [3/4] Unpacking archives with extractcode"
    extractcode --shallow "$ARTIFACTS_DIR"
fi

echo "==> [4/4] Running scancode (this will take 30+ minutes)"

# Glob patterns scancode should skip outright. These files carry no usable
# licence/copyright text — they're compiled bytecode, native binaries, original
# archives already unpacked by extractcode, or opaque media. Skipping them
# avoids hundreds of thousands of useless file scans.
SCANCODE_IGNORES=(
    # JVM / Kotlin / Android compiled bytecode
    '*.class' '*.dex' '*.kotlin_module' '*.kotlin_builtins' '*.kotlin_metadata'
    # Compiled native libraries and object files
    '*.so' '*.dylib' '*.dll' '*.jnilib' '*.o' '*.a' '*.obj' '*.lib' '*.wasm'
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
    --html-app "$OUTPUT_DIR/scan-summary.html"
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

scancode "${SCANCODE_ARGS[@]}" "$ARTIFACTS_DIR"

echo
echo "SBOM outputs:"
ls -lh "$OUTPUT_DIR"/scan.* "$OUTPUT_DIR"/scan-summary.html 2>/dev/null || true
