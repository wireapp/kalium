#!/bin/zsh

set -euo pipefail

script_dir="${0:A:h}"
repo_root="${script_dir:A:h:h}"
core_framework_dir="${repo_root}/sample/nse-feasibility/build/bin/macosArm64/debugFramework"
avs_framework_dir="${repo_root}/sample/nse-feasibility-avs-isolation/build/bin/macosArm64/debugFramework"
core_framework="${core_framework_dir}/KaliumNseFeasibility.framework/KaliumNseFeasibility"
avs_framework="${avs_framework_dir}/KaliumNseAvsIsolation.framework/KaliumNseAvsIsolation"
host_source="${script_dir}/split-framework-host/SplitFrameworkHost.swift"
host_binary_core_first="${TMPDIR%/}/kalium-nse-split-framework-host-core-first"
host_binary_avs_first="${TMPDIR%/}/kalium-nse-split-framework-host-avs-first"
module_cache="${TMPDIR%/}/kalium-nse-split-framework-module-cache"
shared_root="${1:-${TMPDIR%/}/kalium-nse-split-framework-probe}"

if [[ ! -f "$core_framework" || ! -f "$avs_framework" ]]; then
    print -u2 "Missing split frameworks. Build them with:"
    print -u2 "./gradlew :sample:nse-feasibility:linkDebugFrameworkMacosArm64 \\\"
    print -u2 "  :sample:nse-feasibility-avs-isolation:linkDebugFrameworkMacosArm64 \\\"
    print -u2 "  -PUSE_UNIFIED_CORE_CRYPTO=true -PnseFeasibility.includeAvs=false"
    exit 66
fi

mkdir -p "$module_cache"

CLANG_MODULE_CACHE_PATH="$module_cache" SWIFT_MODULECACHE_PATH="$module_cache" xcrun swiftc "$host_source" \
    -F "$core_framework_dir" \
    -F "$avs_framework_dir" \
    -framework KaliumNseFeasibility \
    -framework KaliumNseAvsIsolation \
    -Xlinker -rpath -Xlinker "$core_framework_dir" \
    -Xlinker -rpath -Xlinker "$avs_framework_dir" \
    -o "$host_binary_avs_first"

CLANG_MODULE_CACHE_PATH="$module_cache" SWIFT_MODULECACHE_PATH="$module_cache" xcrun swiftc "$host_source" \
    -F "$core_framework_dir" \
    -F "$avs_framework_dir" \
    -framework KaliumNseAvsIsolation \
    -framework KaliumNseFeasibility \
    -Xlinker -rpath -Xlinker "$core_framework_dir" \
    -Xlinker -rpath -Xlinker "$avs_framework_dir" \
    -o "$host_binary_core_first"

"$host_binary_avs_first" "$shared_root/avs-first" avs-first
"$host_binary_core_first" "$shared_root/core-first" core-first
