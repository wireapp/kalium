#!/usr/bin/env python3
"""Run native tests with the local X.509 fixture on a disposable simulator."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile

from x509_fixture_server import X509FixtureServer


REPO = Path(__file__).resolve().parents[2]


def run(*command):
    return subprocess.run(command, cwd=REPO, check=True, text=True, capture_output=True).stdout.strip()


def select_simulator(runtimes):
    # A fresh self-hosted runner may have runtimes but no created devices.
    for runtime in runtimes:
        if not runtime["isAvailable"] or ".iOS-" not in runtime["identifier"]:
            continue
        for device_type in runtime.get("supportedDeviceTypes", []):
            if device_type["productFamily"] == "iPhone":
                return runtime["identifier"], device_type["identifier"]
    raise RuntimeError(
        "No available iOS runtime with an iPhone device type. "
        "Install an iOS Simulator runtime for the selected Xcode on this runner."
    )


def main():
    runtimes = json.loads(run("xcrun", "simctl", "list", "runtimes", "--json"))["runtimes"]
    runtime, device_type = select_simulator(runtimes)
    simulator = run("xcrun", "simctl", "create", "Kalium native tests", device_type, runtime)
    try:
        run("xcrun", "simctl", "bootstatus", simulator, "-b")
        with tempfile.TemporaryDirectory(prefix="kalium-ios-tests-") as directory:
            work = Path(directory)
            config = work / "tls.cnf"
            config.write_text("""[req]
distinguished_name=dn
x509_extensions=ext
prompt=no
[dn]
CN=Kalium native test fixture
[ext]
subjectAltName=DNS:localhost,IP:127.0.0.1
basicConstraints=critical,CA:TRUE
keyUsage=critical,digitalSignature,keyEncipherment,keyCertSign
extendedKeyUsage=serverAuth
""")
            certificate, key = work / "certificate.pem", work / "key.pem"
            run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", str(key),
                "-out", str(certificate), "-days", "1", "-config", str(config))
            run("xcrun", "simctl", "keychain", simulator, "add-root-cert", str(certificate))
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
        subprocess.run(["xcrun", "simctl", "shutdown", simulator], check=False)
        subprocess.run(["xcrun", "simctl", "delete", simulator], check=False)


if __name__ == "__main__":
    sys.exit(main())
