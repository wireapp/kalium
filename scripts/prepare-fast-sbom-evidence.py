#!/usr/bin/env python3
"""Collect only metadata and legal notices needed by the fast SBOM pipeline.

Dependency archives stay in Gradle's cache. This script reads their ZIP central
directories and materialises LICENSE/NOTICE/COPYING files, including notices in
nested JARs, without extracting compiled contents. For Kotlin/JS it copies only
package.json and legal notice files from resolved node_modules trees.

Usage: prepare-fast-sbom-evidence.py SBOM_DIR JS_BUILD_DIR NATIVE_LIBS_DIR
"""

import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import sys
import zipfile


LEGAL_PREFIXES = ("license", "licence", "notice", "copying", "unlicense")
ARCHIVE_SUFFIXES = (".jar", ".aar", ".klib", ".war", ".zip")
MAX_LEGAL_FILE_BYTES = 8 * 1024 * 1024
MAX_NESTED_ARCHIVE_BYTES = 256 * 1024 * 1024
MAX_ARCHIVE_DEPTH = 3


def is_legal_name(name):
    return PurePosixPath(name).name.lower().startswith(LEGAL_PREFIXES)


def safe_relative(name):
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts:
        return None
    return Path(*path.parts)


def write_zip_legal_files(archive, destination, prefix=Path(), depth=0):
    written = 0
    try:
        with zipfile.ZipFile(archive) as zipped:
            for info in zipped.infolist():
                if info.is_dir():
                    continue
                relative = safe_relative(info.filename)
                if relative is None:
                    continue
                if is_legal_name(info.filename):
                    if info.file_size > MAX_LEGAL_FILE_BYTES:
                        continue
                    target = destination / prefix / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with zipped.open(info) as source, target.open("wb") as output:
                        shutil.copyfileobj(source, output)
                    written += 1
                    continue
                if depth >= MAX_ARCHIVE_DEPTH:
                    continue
                if not info.filename.lower().endswith(ARCHIVE_SUFFIXES):
                    continue
                if info.file_size > MAX_NESTED_ARCHIVE_BYTES:
                    continue
                data = zipped.read(info)
                if not zipfile.is_zipfile(io.BytesIO(data)):
                    continue
                nested_prefix = prefix / Path(*relative.parts) / "nested"
                written += write_zip_legal_files(
                    io.BytesIO(data), destination, nested_prefix, depth + 1
                )
    except (OSError, zipfile.BadZipFile, RuntimeError):
        return written
    return written


def artifact_paths(fragments_dir):
    paths = set()
    for fragment in sorted(Path(fragments_dir).glob("*.tsv")):
        for line in fragment.read_text(encoding="utf-8").splitlines():
            fields = line.split("\t")
            if len(fields) == 3 and fields[0] == "A":
                paths.add(fields[2])
    return sorted(paths)


def collect_maven_evidence(fragments_dir, artifacts_dir):
    archives = legal_files = 0
    seen_content = set()
    for raw_path in artifact_paths(fragments_dir):
        source = Path(raw_path)
        if not source.is_file() or not source.name.lower().endswith(ARCHIVE_SUFFIXES):
            continue
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if digest in seen_content:
            continue
        seen_content.add(digest)
        destination = artifacts_dir / "maven" / digest[:12] / f"{source.name}-extract"
        count = write_zip_legal_files(source, destination)
        # Keep an empty package directory: the audit must report dependencies
        # that do not bundle a legal notice instead of silently omitting them.
        destination.mkdir(parents=True, exist_ok=True)
        archives += 1
        legal_files += count
    return archives, legal_files


def copy_package_evidence(package_json, package_name, npm_root):
    package_dir = package_json.parent
    identity = hashlib.sha256(str(package_dir).encode()).hexdigest()[:12]
    if "node_modules" in package_dir.parts:
        node_modules_index = max(
            index for index, part in enumerate(package_dir.parts) if part == "node_modules"
        )
        package_suffix = Path(*package_dir.parts[node_modules_index + 1 :])
    else:
        # Kotlin/JS keeps imported npm packages in packages_imported/ and exposes
        # them through symlinks under node_modules. pathlib does not follow those
        # symlinks during rglob, so materialise the package by its declared name.
        package_suffix = Path(*package_name.split("/"))
    destination = npm_root / identity / "node_modules" / package_suffix
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copy2(package_json, destination / "package.json")

    copied = 0
    for root, dirs, files in os.walk(package_dir):
        dirs[:] = [directory for directory in dirs if directory != "node_modules"]
        root_path = Path(root)
        for name in files:
            if not is_legal_name(name):
                continue
            source = root_path / name
            if source.stat().st_size > MAX_LEGAL_FILE_BYTES:
                continue
            relative = source.relative_to(package_dir)
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            copied += 1
    return copied


def collect_npm_evidence(js_build_dir, artifacts_dir):
    js_root = Path(js_build_dir)
    npm_root = artifacts_dir / "npm"
    if not js_root.is_dir():
        return 0, 0

    packages = legal_files = 0
    for package_json in sorted(js_root.rglob("package.json")):
        if not ({"node_modules", "packages_imported"} & set(package_json.parts)):
            continue
        try:
            data = json.loads(package_json.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if not data.get("name") or not data.get("version"):
            continue
        legal_files += copy_package_evidence(package_json, data["name"], npm_root)
        packages += 1

    manifests = npm_root / "_workspace_manifests"
    packages_root = js_root / "packages"
    if packages_root.is_dir():
        for package_json in sorted(packages_root.rglob("package.json")):
            if "node_modules" in package_json.parts:
                continue
            relative = package_json.relative_to(packages_root)
            target = manifests / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(package_json, target)
    return packages, legal_files


def collect_native_evidence(native_libs_dir, artifacts_dir):
    source_root = Path(native_libs_dir)
    if not source_root.is_dir():
        return 0
    destination_root = artifacts_dir / "native" / "avs"
    destination_root.mkdir(parents=True, exist_ok=True)
    count = 0
    for source in source_root.rglob("*"):
        if not source.is_file() or not is_legal_name(source.name):
            continue
        target = destination_root / source.relative_to(source_root)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        count += 1
    return count


def main():
    if len(sys.argv) != 4:
        print(
            "Usage: prepare-fast-sbom-evidence.py SBOM_DIR JS_BUILD_DIR NATIVE_LIBS_DIR",
            file=sys.stderr,
        )
        sys.exit(2)
    sbom_dir = Path(sys.argv[1])
    artifacts_dir = sbom_dir / "artifacts"
    shutil.rmtree(artifacts_dir, ignore_errors=True)
    artifacts_dir.mkdir(parents=True)

    archives, maven_legal = collect_maven_evidence(
        sbom_dir / "fragments", artifacts_dir
    )
    npm_packages, npm_legal = collect_npm_evidence(sys.argv[2], artifacts_dir)
    native_legal = collect_native_evidence(sys.argv[3], artifacts_dir)
    print(
        f"  Collected evidence from {archives} unique Maven archives and "
        f"{npm_packages} npm package occurrences"
    )
    print(
        f"  Materialised {maven_legal + npm_legal + native_legal} legal notice files"
    )


if __name__ == "__main__":
    main()
