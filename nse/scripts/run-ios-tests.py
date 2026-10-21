#!/usr/bin/env python3
"""Run native tests with the local X.509 fixture on a disposable simulator."""

from pathlib import Path
import subprocess
import sys

from ios_test_support import create_simulator, delete_simulator, run
from x509_fixture_server import serving_x509_fixture


REPO = Path(__file__).resolve().parents[2]


def main():
    simulator, _ = create_simulator("Kalium native tests")
    try:
        run("xcrun", "simctl", "bootstatus", simulator, "-b")
        with serving_x509_fixture(simulator):
            print(f"Running native tests on temporary simulator {simulator}", flush=True)
            return subprocess.run(
                ["./gradlew", "iosSimulatorArm64Test", "--device", simulator, *sys.argv[1:]],
                cwd=REPO,
                check=False,
            ).returncode
    finally:
        delete_simulator(simulator)


if __name__ == "__main__":
    sys.exit(main())
