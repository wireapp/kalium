#!/usr/bin/env python3
"""Convert Kalium's validated CycloneDX component graph to SPDX 2.3 Tag/Value.

The conversion is intentionally package-level. File evidence remains a deep-audit
extra and is not part of the shared customer delivery contract.
"""

from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import sys


def spdx_id(reference):
    digest = hashlib.sha256(reference.encode()).hexdigest()[:20]
    return f"SPDXRef-Package-{digest}"


def single_line(value):
    return " ".join(str(value or "").split())


def license_expression(component, default="NOASSERTION"):
    identifiers = []
    for choice in component.get("licenses", []):
        license_data = choice.get("license", {})
        identifier = license_data.get("id")
        if identifier and identifier not in identifiers:
            identifiers.append(identifier)
    if not identifiers:
        return default
    if len(identifiers) == 1:
        return identifiers[0]
    return "(" + " OR ".join(identifiers) + ")"


def creation_time():
    try:
        value = subprocess.check_output(
            ["git", "show", "-s", "--format=%cI", "HEAD"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
        return datetime.fromisoformat(value).astimezone(timezone.utc).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
    except (OSError, subprocess.CalledProcessError, ValueError):
        return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def package_lines(component, root=False):
    reference = component["bom-ref"]
    license_id = license_expression(
        component,
        default="GPL-3.0-or-later" if root else "NOASSERTION",
    )
    lines = [
        f"PackageName: {single_line(component.get('name'))}",
        f"SPDXID: {spdx_id(reference)}",
        f"PackageVersion: {single_line(component.get('version'))}",
        "PackageDownloadLocation: NOASSERTION",
        "FilesAnalyzed: false",
        f"PackageLicenseConcluded: {license_id}",
        f"PackageLicenseDeclared: {license_id}",
        "PackageCopyrightText: NOASSERTION",
    ]
    purl = component.get("purl")
    if purl:
        lines.append(f"ExternalRef: PACKAGE-MANAGER purl {purl}")
    return lines


def convert(document):
    root = document["metadata"]["component"]
    components = [root] + sorted(
        document.get("components", []), key=lambda component: component["bom-ref"]
    )
    known = {component["bom-ref"] for component in components}
    namespace_hash = hashlib.sha256(
        json.dumps(document, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    lines = [
        "SPDXVersion: SPDX-2.3",
        "DataLicense: CC0-1.0",
        "SPDXID: SPDXRef-DOCUMENT",
        f"DocumentName: Kalium-{single_line(root.get('version'))}",
        f"DocumentNamespace: https://wire.com/spdx/kalium/{namespace_hash}",
        "Creator: Organization: Wire Swiss GmbH",
        "Creator: Tool: Kalium fast SBOM generator",
        f"Created: {creation_time()}",
        "",
    ]
    for component in components:
        lines.extend(package_lines(component, root=component is root))
        lines.append("")

    lines.append(f"Relationship: SPDXRef-DOCUMENT DESCRIBES {spdx_id(root['bom-ref'])}")
    for dependency in sorted(
        document.get("dependencies", []), key=lambda item: item["ref"]
    ):
        parent = dependency["ref"]
        if parent not in known:
            raise ValueError(f"unknown relationship parent: {parent}")
        for child in sorted(dependency.get("dependsOn", [])):
            if child not in known:
                raise ValueError(f"unknown relationship child: {child}")
            lines.append(
                f"Relationship: {spdx_id(parent)} DEPENDS_ON {spdx_id(child)}"
            )
    return "\n".join(lines) + "\n"


def validate_tag_value(content):
    required_headers = (
        "SPDXVersion: SPDX-2.3\n",
        "DataLicense: CC0-1.0\n",
        "SPDXID: SPDXRef-DOCUMENT\n",
        "DocumentNamespace: ",
        "Creator: ",
        "Created: ",
    )
    for header in required_headers:
        if header not in content:
            raise ValueError(f"missing SPDX document field: {header.strip()}")
    package_ids = {
        line.removeprefix("SPDXID: ")
        for line in content.splitlines()
        if line.startswith("SPDXID: SPDXRef-Package-")
    }
    if not package_ids:
        raise ValueError("SPDX document contains no packages")
    for line in content.splitlines():
        if not line.startswith("Relationship: "):
            continue
        _, left, _relationship, right = line.split()
        if left != "SPDXRef-DOCUMENT" and left not in package_ids:
            raise ValueError(f"unknown SPDX relationship source: {left}")
        if right not in package_ids:
            raise ValueError(f"unknown SPDX relationship target: {right}")


def main():
    if len(sys.argv) != 3:
        print("Usage: cyclonedx-to-spdx.py INPUT_CDX_JSON OUTPUT_SPdx", file=sys.stderr)
        sys.exit(2)
    document = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    content = convert(document)
    validate_tag_value(content)
    Path(sys.argv[2]).write_text(content, encoding="utf-8")
    package_count = content.count("\nPackageName: ")
    print(f"  Wrote {sys.argv[2]} with {package_count} SPDX packages")


if __name__ == "__main__":
    main()
