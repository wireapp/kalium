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
#   - extractcode + scancode on PATH (ScanCode-Toolkit, https://github.com/aboutcode-org/scancode-toolkit)
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

echo "==> [1/4] Setting up VENV and ScanCode..."
if [ ! -d ".venv" ]; then
    python3 -m venv .venv
fi
source .venv/bin/activate
echo

# If this fails because of ICU, you might have to run:
# brew brew install pkg-config icu4c
# export PATH="$(brew --prefix)/opt/icu4c/bin:$(brew --prefix)/opt/icu4c/sbin:$PATH"
# export PKG_CONFIG_PATH="$(brew --prefix)/opt/icu4c/lib/pkgconfig:$PKG_CONFIG_PATH"
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

echo "==> [2/4] Resolving and copying dependency artifacts via Gradle"
# shellcheck disable=SC2086
./gradlew :collectSbomArtifacts $SBOM_GRADLE_EXTRA_ARGS

if [[ ! -d "$ARTIFACTS_DIR" ]]; then
    echo "ERROR: $ARTIFACTS_DIR was not produced by Gradle." >&2
    exit 1
fi

echo "==> [3/4] Unpacking archives with extractcode"
extractcode --shallow "$ARTIFACTS_DIR"

echo "==> [4/4] Running scancode (this will take 30+ minutes)"
SCANCODE_ARGS=(
    --license --copyright --package --info --email --url
    --license-text --license-references --notice-text
    --processes "$SCANCODE_PROCESSES"
    --json-pp "$OUTPUT_DIR/scan.json"
    --spdx-tv "$OUTPUT_DIR/scan.spdx"
    --html-app "$OUTPUT_DIR/scan-summary.html"
)
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
