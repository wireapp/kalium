#!/usr/bin/env bash
# Generate the normal release SBOM from resolved dependency graphs.
# Use generate-sbom.sh only when a deep file-level ScanCode audit is required.

set -euo pipefail

cd "$(dirname "$0")/.."

OUTPUT_DIR="build/sbom-fast"
ARTIFACTS_DIR="$OUTPUT_DIR/artifacts"
POMS_DIR="$OUTPUT_DIR/poms"
CDX_FILE="$OUTPUT_DIR/scan.cdx.json"
RAW_CDX_FILE="$OUTPUT_DIR/kalium.raw.cdx.json"
SPDX_FILE="$OUTPUT_DIR/scan.spdx"
NOTICE_FILE="THIRD-PARTY-NOTICE.md"
OVERRIDES="scripts/sbom-license-overrides.yaml"
SBOM_GRADLE_EXTRA_ARGS="${SBOM_GRADLE_EXTRA_ARGS:-}"

if [[ "$(uname -s)" == "Darwin" && "$SBOM_GRADLE_EXTRA_ARGS" != *"USE_UNIFIED_CORE_CRYPTO"* ]]; then
    SBOM_GRADLE_EXTRA_ARGS="-PUSE_UNIFIED_CORE_CRYPTO=true $SBOM_GRADLE_EXTRA_ARGS"
fi
if [[ "$SBOM_GRADLE_EXTRA_ARGS" != *"kalium.providerCacheScope"* ]]; then
    SBOM_GRADLE_EXTRA_ARGS="-Pkalium.providerCacheScope=LOCAL $SBOM_GRADLE_EXTRA_ARGS"
fi

if ! python3 -c 'import yaml' 2>/dev/null; then
    echo "ERROR: Python 3 with PyYAML is required." >&2
    exit 1
fi

echo "==> [1/6] Resolving production dependency graphs"
# shellcheck disable=SC2086
./gradlew generateFastSbom $SBOM_GRADLE_EXTRA_ARGS

echo "==> [2/6] Collecting selective license evidence"
python3 scripts/prepare-fast-sbom-evidence.py "$OUTPUT_DIR" build/js native/libs

echo "==> [3/6] Parsing Maven license metadata"
python3 scripts/parse-pom-licenses.py \
    "$POMS_DIR" \
    "$OUTPUT_DIR/scan-pom-licenses.tsv" \
    "$OUTPUT_DIR/scan-pom-no-licenses.txt" \
    "$OVERRIDES"

echo "==> [4/6] Auditing bundled license files"
python3 scripts/audit-license-files.py "$ARTIFACTS_DIR" "$OUTPUT_DIR"

echo "==> [5/6] Generating notice and enriching CycloneDX"
python3 scripts/generate-third-party-notice.py "$OUTPUT_DIR" "$NOTICE_FILE"
python3 scripts/finalize-fast-sbom.py \
    "$RAW_CDX_FILE" \
    "$CDX_FILE" \
    "$OUTPUT_DIR/scan-pom-licenses.tsv" \
    "$ARTIFACTS_DIR" \
    "$OVERRIDES"
python3 scripts/cyclonedx-to-spdx.py "$CDX_FILE" "$SPDX_FILE"

echo "==> [6/6] Bundling customer deliverables"
python3 scripts/bundle-sbom.py "$OUTPUT_DIR" "$NOTICE_FILE"

echo
echo "Fast SBOM outputs:"
ls -lh "$CDX_FILE" "$SPDX_FILE" "$OUTPUT_DIR/SBOM-and-license.zip" "$NOTICE_FILE"
