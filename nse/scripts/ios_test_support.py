"""Disposable simulator and local TLS certificate shared by the iOS test scripts."""

import json
from pathlib import Path
import subprocess


TLS_CONFIG = """[req]
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
"""


def run(*command):
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"{Path(command[0]).name} failed:\n{result.stdout}\n{result.stderr}")
    return result.stdout.strip()


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


def create_simulator(name):
    """Creates an iPhone simulator on the first available iOS runtime; returns its UDID and runtime, e.g. iOS-18-4."""
    runtimes = json.loads(run("xcrun", "simctl", "list", "runtimes", "--json"))["runtimes"]
    runtime, device_type = select_simulator(runtimes)
    return run("xcrun", "simctl", "create", name, device_type, runtime), runtime.rsplit(".", 1)[-1]


def delete_simulator(simulator):
    subprocess.run(["xcrun", "simctl", "shutdown", simulator], check=False)
    subprocess.run(["xcrun", "simctl", "delete", simulator], check=False)


def trust_local_certificate(simulator, directory):
    """Creates a localhost certificate in directory, trusts it on the simulator and returns the certificate and key."""
    directory = Path(directory)
    config = directory / "tls.cnf"
    config.write_text(TLS_CONFIG)
    certificate, key = directory / "certificate.pem", directory / "key.pem"
    run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", str(key),
        "-out", str(certificate), "-days", "1", "-config", str(config))
    run("xcrun", "simctl", "keychain", simulator, "add-root-cert", str(certificate))
    return certificate, key
