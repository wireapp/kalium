#!/usr/bin/env python3
"""Audit pinned AVS event behavior and artifact metadata; this is not extension-safety certification."""
import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import subprocess
import tempfile

REPO = Path(__file__).resolve().parents[2]
SHA = "637e71bb26ae8d50446e500fd4141bcdb8ee4a13bc6bb1d861d6d656780e2d77"
CACHE = Path.home() / ".gradle/caches/kalium-apple-avs-runtime" / SHA
FRAMEWORK = CACHE / "frameworks-v1/avs.xcframework"
REPORT = REPO / "nse/build/reports/avs-event-api"
CASES = ["incoming-video", "missed", "closed", "recreate", "two-conversations",
         "two-distinct-sessions", "other-self-client-reject", "current-self-client-reject", "expired", "malformed"]


def run(*args, check=True):
    return subprocess.run(args, text=True, capture_output=True, check=check, timeout=120)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--simulator", help="Use an already booted simulator instead of a temporary one")
    options = parser.parse_args()
    assert hashlib.sha256((CACHE / "avs.xcframework.zip").read_bytes()).hexdigest() == SHA
    info = plistlib.loads((FRAMEWORK / "Info.plist").read_bytes())
    simulator_slice = next(item["LibraryIdentifier"] for item in info["AvailableLibraries"]
                           if item.get("SupportedPlatformVariant") == "simulator" and "arm64" in item["SupportedArchitectures"])
    simulation = FRAMEWORK / simulator_slice
    device = FRAMEWORK / "ios-arm64/avs.framework/avs"
    metadata = {}
    for label, binary in [("device", device), ("simulator", simulation / "avs.framework/avs")]:
        headers = run("xcrun", "otool", "-hv", str(binary)).stdout
        metadata[label] = {"extensionSafeFlag": "APP_EXTENSION_SAFE" in headers, "machHeaders": headers}
    imports = run("xcrun", "nm", "-u", str(device)).stdout
    strings = run("strings", str(device)).stdout
    metadata["device"]["importsUIApplication"] = "_OBJC_CLASS_$_UIApplication" in imports
    metadata["device"]["containsSharedApplication"] = "sharedApplication" in strings
    REPORT.mkdir(parents=True, exist_ok=True)
    simulator = options.simulator
    outcomes = []
    try:
        if simulator is None:
            simulator = run("xcrun", "simctl", "create", "Kalium AVS event audit",
                            "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro",
                            "com.apple.CoreSimulator.SimRuntime.iOS-26-3").stdout.strip()
            run("xcrun", "simctl", "boot", simulator)
            run("xcrun", "simctl", "bootstatus", simulator, "-b")
        with tempfile.TemporaryDirectory(prefix="kalium-avs-event-") as temporary:
            executable = Path(temporary) / "event-probe"
            sdk = run("xcrun", "--sdk", "iphonesimulator", "--show-sdk-path").stdout.strip()
            run("xcrun", "--sdk", "iphonesimulator", "clang", "-isysroot", sdk,
                "-target", "arm64-apple-ios15.0-simulator", "-fapplication-extension",
                "-F", str(simulation), "-framework", "avs", "-Wl,-rpath," + str(simulation),
                str(REPO / "nse/src/callingTest/Events.c"), "-o", str(executable))
            for case in CASES:
                result = run("xcrun", "simctl", "spawn", simulator, str(executable), case, check=False)
                (REPORT / (case + ".log")).write_text(result.stdout + result.stderr)
                assert result.returncode == 0, case + ": " + result.stdout + result.stderr
                line = next(line for line in result.stdout.splitlines() if line.startswith("AVS_EVENT_RESULT="))
                outcomes.append(json.loads(line.removeprefix("AVS_EVENT_RESULT=")))
                print(json.dumps(outcomes[-1]), flush=True)
        by_case = {outcome["case"]: outcome for outcome in outcomes}
        assert by_case["missed"]["missed"] == 1 and by_case["closed"]["closed"] == 1
        assert by_case["incoming-video"]["incoming"] == 1
        # create reuses the permanent account context: end evaluates a batch, it does not destroy
        # the handle. This negative control explains why the Apple wrapper must retain its context.
        assert by_case["recreate"]["reusedHandle"] and by_case["recreate"]["newContextCallbacks"] == 0
        assert by_case["recreate"]["incoming"] == 2
        assert by_case["two-conversations"]["missed"] == 0
        assert by_case["two-conversations"]["incoming"] == 2 and by_case["two-conversations"]["closed"] == 1
        # Keep multi-conversation observations separate from lifecycle assertions. The original
        # fixture reused a session ID; neither result alone establishes a replacement-artifact requirement.
        assert not metadata["device"]["extensionSafeFlag"] and not metadata["simulator"]["extensionSafeFlag"]
        assert metadata["device"]["importsUIApplication"] and metadata["device"]["containsSharedApplication"]
        (REPORT / "result.json").write_text(json.dumps({"auditCompleted": True, "extensionSafetyEstablished": False,
            "version": "10.5.19", "archiveSha256": SHA, "sourceCommit": "4ca13dde13759a2081b6ccaa2c2ac4756f7f20fd",
            "extensionOnlyLinkSucceeded": True, "artifact": metadata, "observations": outcomes}, indent=2) + "\n")
    finally:
        if not options.simulator and simulator:
            run("xcrun", "simctl", "shutdown", simulator, check=False)
            run("xcrun", "simctl", "delete", simulator, check=False)


if __name__ == "__main__":
    main()
