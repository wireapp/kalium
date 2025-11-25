#!/bin/bash

# Wire
# Copyright (C) 2024 Wire Swiss GmbH
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see http://www.gnu.org/licenses/.

set -e

# Makes all paths relative to project root so it can be run from anywhere
parent_path=$(
  cd "$(dirname "${BASH_SOURCE[0]}")" || exit
  pwd -P
)
cd "$parent_path/.." || exit

# Configuration
CORE_CRYPTO_VERSION="${1:-9.1.1}"
FRAMEWORKS_DIR="cryptography/frameworks"
GITHUB_RELEASE_URL="https://github.com/wireapp/core-crypto/releases/download/v${CORE_CRYPTO_VERSION}"

# Framework names
CORE_CRYPTO_FRAMEWORK="WireCoreCrypto"
CORE_CRYPTO_UNIFFI_FRAMEWORK="WireCoreCryptoUniffi"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if curl is available
if ! command -v curl &> /dev/null; then
    print_error "curl is required but not installed. Please install curl and try again."
    exit 1
fi

# Check if unzip is available
if ! command -v unzip &> /dev/null; then
    print_error "unzip is required but not installed. Please install unzip and try again."
    exit 1
fi

print_info "Downloading CoreCrypto iOS frameworks version ${CORE_CRYPTO_VERSION}"

# Create frameworks directory
mkdir -p "${FRAMEWORKS_DIR}"

# Function to download and extract a framework
download_framework() {
    local framework_name=$1
    local zip_file="${FRAMEWORKS_DIR}/${framework_name}.xcframework.zip"
    local xcframework_dir="${FRAMEWORKS_DIR}/${framework_name}.xcframework"
    local download_url="${GITHUB_RELEASE_URL}/${framework_name}.xcframework.zip"

    # Check if framework already exists
    if [ -d "${xcframework_dir}" ]; then
        print_warning "${framework_name}.xcframework already exists. Removing old version..."
        rm -rf "${xcframework_dir}"
    fi

    # Download
    print_info "Downloading ${framework_name}.xcframework..."
    if ! curl -L -f -o "${zip_file}" "${download_url}"; then
        print_error "Failed to download ${framework_name}.xcframework from ${download_url}"
        exit 1
    fi

    # Extract
    print_info "Extracting ${framework_name}.xcframework..."
    if ! unzip -q -o "${zip_file}" -d "${FRAMEWORKS_DIR}"; then
        print_error "Failed to extract ${framework_name}.xcframework"
        rm -f "${zip_file}"
        exit 1
    fi

    # Clean up zip file
    rm -f "${zip_file}"

    # Verify extraction
    if [ ! -d "${xcframework_dir}" ]; then
        print_error "${framework_name}.xcframework was not extracted correctly"
        exit 1
    fi

    print_info "${framework_name}.xcframework downloaded and extracted successfully"
}

# Download both frameworks
download_framework "${CORE_CRYPTO_FRAMEWORK}"
download_framework "${CORE_CRYPTO_UNIFFI_FRAMEWORK}"

# Create a version file to track which version is installed
echo "${CORE_CRYPTO_VERSION}" > "${FRAMEWORKS_DIR}/.core-crypto-version"

# Print summary
echo ""
print_info "========================================"
print_info "CoreCrypto iOS frameworks setup complete"
print_info "========================================"
print_info "Version: ${CORE_CRYPTO_VERSION}"
print_info "Location: ${FRAMEWORKS_DIR}/"
echo ""
print_info "Frameworks installed:"
ls -la "${FRAMEWORKS_DIR}/" | grep -E "\.xcframework$|\.core-crypto-version$"
echo ""
print_info "To use a different version, run:"
echo "  ./scripts/download-core-crypto-ios.sh <version>"
echo "  Example: ./scripts/download-core-crypto-ios.sh 9.1.1"