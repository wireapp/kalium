#!/usr/bin/env python3
"""Enrich and structurally validate the fast CycloneDX SBOM."""

import csv
import importlib.util
import json
import os
from pathlib import Path
import sys
from urllib.parse import quote

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if _SCRIPT_DIR not in sys.path:
    sys.path.insert(0, _SCRIPT_DIR)

from sbom_overrides import load_overrides


def load_notice_module():
    path = Path(__file__).with_name("generate-third-party-notice.py")
    spec = importlib.util.spec_from_file_location("sbom_notice", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def license_entries(names, urls, notice, license_index):
    spdx_ids, unresolved = notice.resolve_licenses(names, license_index)
    if unresolved:
        raise ValueError(f"unresolved license names: {', '.join(unresolved)}")
    url_list = [value.strip() for value in (urls or "").split("|")]
    entries = []
    for index, spdx in enumerate(spdx_ids):
        license_data = {"id": spdx}
        if index < len(url_list) and url_list[index]:
            license_data["url"] = url_list[index]
        entries.append({"license": license_data})
    return entries


def pom_licenses(path):
    result = {}
    with open(path, encoding="utf-8") as source:
        for row in csv.DictReader(source, delimiter="\t"):
            result[(row["groupId"], row["artifactId"], row["version"])] = row
    return result


def append_npm_components(document, artifacts_dir, notice, license_index, overrides):
    closure = notice.compute_runtime_closure(artifacts_dir)
    maven_names = {component.get("name", "") for component in document["components"]}
    existing_refs = {component["bom-ref"] for component in document["components"]}
    seen = set()
    added_refs = []

    for package in notice.discover_npm_packages(artifacts_dir):
        identity = (package["name"], package["version"])
        if identity in seen:
            continue
        seen.add(identity)
        override = overrides.get(f"npm:{package['name']}")
        if override:
            names, urls = override["names"], override["urls"]
        else:
            if closure is not None and package["name"] not in closure:
                continue
            if not package["license_names"] and notice.npm_name_likely_duplicates_maven(
                package["name"], maven_names
            ):
                continue
            names, urls = package["license_names"], package["license_urls"]

        encoded_name = quote(package["name"], safe="")
        ref = f"pkg:npm/{encoded_name}@{quote(package['version'], safe='')}"
        if ref in existing_refs:
            continue
        component = {
            "type": "library",
            "bom-ref": ref,
            "name": package["name"],
            "version": package["version"],
            "scope": "required",
            "purl": ref,
            "properties": [{"name": "wire:kalium:targets", "value": "js"}],
        }
        licenses = license_entries(names, urls, notice, license_index)
        if licenses:
            component["licenses"] = licenses
        document["components"].append(component)
        document["dependencies"].append({"ref": ref, "dependsOn": []})
        existing_refs.add(ref)
        added_refs.append(ref)

    root_ref = document["metadata"]["component"]["bom-ref"]
    root_dependency = next(
        dependency for dependency in document["dependencies"] if dependency["ref"] == root_ref
    )
    root_dependency.setdefault("dependsOn", []).extend(added_refs)
    root_dependency["dependsOn"] = sorted(set(root_dependency["dependsOn"]))
    return len(added_refs)


def validate(document):
    if document.get("bomFormat") != "CycloneDX" or document.get("specVersion") != "1.6":
        raise ValueError("expected a CycloneDX 1.6 document")
    component_refs = [component.get("bom-ref") for component in document.get("components", [])]
    if None in component_refs or len(component_refs) != len(set(component_refs)):
        raise ValueError("component bom-ref values must be present and unique")
    root_ref = document.get("metadata", {}).get("component", {}).get("bom-ref")
    known_refs = set(component_refs) | {root_ref}
    dependency_refs = {dependency.get("ref") for dependency in document.get("dependencies", [])}
    if dependency_refs != known_refs:
        missing = known_refs - dependency_refs
        extra = dependency_refs - known_refs
        raise ValueError(f"dependency refs differ from components; missing={missing}, extra={extra}")
    unknown_children = {
        child
        for dependency in document["dependencies"]
        for child in dependency.get("dependsOn", [])
        if child not in known_refs
    }
    if unknown_children:
        raise ValueError(f"dependency graph contains unknown refs: {unknown_children}")


def main():
    if len(sys.argv) != 6:
        print(
            "Usage: finalize-fast-sbom.py INPUT_CDX OUTPUT_CDX POM_TSV ARTIFACTS_DIR OVERRIDES",
            file=sys.stderr,
        )
        sys.exit(2)
    input_path, output_path, pom_path, artifacts_dir, overrides_path = sys.argv[1:6]
    document = json.loads(Path(input_path).read_text(encoding="utf-8"))
    notice = load_notice_module()
    license_db = notice.load_scancode_license_db("THIRD-PARTY-NOTICE.md")
    if not license_db:
        raise ValueError("no canonical license database available")
    license_index = notice.build_name_to_spdx_index(license_db)
    licenses_by_coordinate = pom_licenses(pom_path)

    licensed_maven = 0
    for component in document.get("components", []):
        coordinate = (component.get("group"), component.get("name"), component.get("version"))
        row = licenses_by_coordinate.get(coordinate)
        if not row:
            continue
        entries = license_entries(
            row["license_names"], row["license_urls"], notice, license_index
        )
        if entries:
            component["licenses"] = entries
            licensed_maven += 1

    overrides = load_overrides(overrides_path)
    npm_count = append_npm_components(
        document, artifacts_dir, notice, license_index, overrides
    )
    document["components"].sort(key=lambda component: component["bom-ref"])
    document["dependencies"].sort(key=lambda dependency: dependency["ref"])
    validate(document)
    Path(output_path).write_text(
        json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(
        f"  Validated {len(document['components'])} components "
        f"({licensed_maven} Maven licenses, {npm_count} npm components added)"
    )


if __name__ == "__main__":
    main()
