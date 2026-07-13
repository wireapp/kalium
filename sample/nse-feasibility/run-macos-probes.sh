#!/bin/zsh

set -euo pipefail

script_dir="${0:A:h}"
repo_root="${script_dir:A:h:h}"
executable="${repo_root}/sample/nse-feasibility/build/bin/macosArm64/debugExecutable/kalium-nse-feasibility.kexe"
shared_root="${1:-${TMPDIR%/}/kalium-nse-feasibility}"

if [[ ! -x "$executable" ]]; then
    print -u2 "Missing probe executable: $executable"
    print -u2 "Build it with: ./gradlew :sample:nse-feasibility:linkDebugExecutableMacosArm64 -PUSE_UNIFIED_CORE_CRYPTO=true"
    exit 66
fi

"$executable" path "$shared_root"
"$executable" lock-try "$shared_root"

"$executable" lock-hold "$shared_root" 60 &
holder_pid=$!
trap 'kill -9 "$holder_pid" 2>/dev/null || true' EXIT
sleep 1

set +e
"$executable" lock-try "$shared_root"
contention_status=$?
set -e
if [[ $contention_status -ne 75 ]]; then
    print -u2 "Expected immediate lock contention exit 75, got $contention_status"
    exit 1
fi

kill -9 "$holder_pid"
wait "$holder_pid" 2>/dev/null || true
trap - EXIT

"$executable" lock-try "$shared_root"
"$executable" corecrypto "$shared_root"
"$executable" network "$shared_root"
"$executable" persistence "$shared_root"
