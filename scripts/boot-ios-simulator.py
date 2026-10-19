#!/usr/bin/env python3
# Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later
"""Boot an available iPhone simulator and select it for Kotlin/Native CI tests."""

import json
import os
import subprocess


def main():
    result = subprocess.run(
        ["xcrun", "simctl", "list", "devices", "available", "--json"],
        check=True,
        capture_output=True,
        text=True,
    )
    devices = json.loads(result.stdout)["devices"]
    runtimes = sorted(
        (runtime for runtime in devices if ".iOS-" in runtime),
        key=lambda runtime: tuple(int(part) for part in runtime.split(".iOS-")[1].split("-")),
        reverse=True,
    )
    for runtime in runtimes:
        iphones = [device for device in devices[runtime] if device["name"].startswith("iPhone") and device["isAvailable"]]
        if iphones:
            device = next((device for device in iphones if device["state"] == "Booted"), iphones[0])
            break
    else:
        raise SystemExit("No available iPhone simulator found. Install an iOS simulator runtime in Xcode.")

    device_id = device["udid"]
    if device["state"] != "Booted":
        subprocess.run(["xcrun", "simctl", "boot", device_id], check=True)
    subprocess.run(["xcrun", "simctl", "bootstatus", device_id, "-b"], check=True)
    print(f"Using {device['name']} on {runtime}: {device_id}")

    if github_env := os.environ.get("GITHUB_ENV"):
        with open(github_env, "a", encoding="utf-8") as environment:
            environment.write(f"KALIUM_IOS_SIMULATOR_UDID={device_id}\n")


if __name__ == "__main__":
    main()
