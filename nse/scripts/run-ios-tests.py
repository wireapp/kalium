#!/usr/bin/env python3
"""Run native tests with the local X.509 fixture on a disposable simulator."""

from pathlib import Path
import subprocess
import sys
import tempfile

from ios_test_support import create_simulator, delete_simulator, run, trust_local_certificate
from x509_fixture_server import X509FixtureServer


REPO = Path(__file__).resolve().parents[2]


def main():
    simulator, _ = create_simulator("Kalium native tests")
    try:
        run("xcrun", "simctl", "bootstatus", simulator, "-b")
        with tempfile.TemporaryDirectory(prefix="kalium-ios-tests-") as directory:
            certificate, key = trust_local_certificate(simulator, directory)
            server = X509FixtureServer(certificate, key)
            try:
                print(f"Running native tests on temporary simulator {simulator}", flush=True)
                return subprocess.run(
                    ["./gradlew", "iosSimulatorArm64Test", "--device", simulator, *sys.argv[1:]],
                    cwd=REPO,
                    check=False,
                ).returncode
            finally:
                server.close()
    finally:
        delete_simulator(simulator)


if __name__ == "__main__":
    sys.exit(main())
