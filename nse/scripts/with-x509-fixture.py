#!/usr/bin/env python3
"""Run a command while a booted simulator trusts the local X.509 fixture and the fixture serves."""

import subprocess
import sys

from x509_fixture_server import serving_x509_fixture


def main():
    if len(sys.argv) < 3:
        raise SystemExit("Usage: with-x509-fixture.py SIMULATOR COMMAND [ARGUMENT...]")
    simulator, command = sys.argv[1], sys.argv[2:]
    with serving_x509_fixture(simulator):
        return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    sys.exit(main())
