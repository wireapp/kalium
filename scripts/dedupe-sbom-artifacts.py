#!/usr/bin/env python3
"""
Deduplicate byte-identical artifact copies in build/sbom/artifacts/.

Gradle's collectSbomArtifacts materialises each resolved third-party
dependency into a per-module subdir (artifacts/jvm/logic/,
artifacts/jvm/core_common/, ...). A jar shared by N Kalium modules ends
up on disk N times. Without dedup, extractcode unpacks all N copies into
sibling *-extract trees and scancode walks every one — pure duplicated
work, since the contents are identical.

This pass keeps the lexicographically first occurrence of each unique
(size, sha1) pair and deletes the rest. Identity is determined by
content hash, so platform-specific natives that share a basename but
not their bytes (libfoo.so per architecture) are preserved.

Scope: ONLY self-contained archive artifacts (.jar, .aar, .klib, .war,
.zip, .tar, .tgz). These are atomic Maven outputs where byte-identity
means same coordinate — collapsing them is safe and saves the bulk of
extractcode/scancode time.

Loose files inside npm/ (already-unpacked packages) and native/ are
explicitly skipped: a file like `safe-buffer/LICENSE` that happens to be
byte-identical across two different `safe-buffer` versions in the tree
belongs to two different packages, and deduping it strips one version's
license attribution.

Usage: dedupe-sbom-artifacts.py <artifacts_dir>
"""

import hashlib
import os
import sys

# Self-contained archive artifacts only. These are what extractcode unpacks,
# so collapsing duplicates here is what actually saves wall-clock time.
ARCHIVE_EXTENSIONS = (
    ".jar", ".aar", ".klib", ".war",
    ".zip", ".tar", ".tar.gz", ".tgz",
)


def is_archive(name: str) -> bool:
    lower = name.lower()
    return any(lower.endswith(ext) for ext in ARCHIVE_EXTENSIONS)


def file_sha1(path, _buf=1 << 16):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(_buf)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def main():
    if len(sys.argv) != 2:
        print("Usage: dedupe-sbom-artifacts.py <artifacts_dir>", file=sys.stderr)
        sys.exit(2)

    root = sys.argv[1]
    if not os.path.isdir(root):
        print(f"ERROR: {root} is not a directory", file=sys.stderr)
        sys.exit(2)

    # Group by size first; only files sharing a size can be byte-identical,
    # so we avoid hashing the long tail of singleton sizes.
    by_size: dict[int, list[str]] = {}
    total_archives = 0
    for dirpath, _dirs, filenames in os.walk(root):
        for name in filenames:
            if not is_archive(name):
                continue
            p = os.path.join(dirpath, name)
            if os.path.islink(p):
                continue
            try:
                size = os.path.getsize(p)
            except OSError:
                continue
            by_size.setdefault(size, []).append(p)
            total_archives += 1

    deleted = 0
    freed = 0
    for size, paths in by_size.items():
        if len(paths) < 2:
            continue
        by_hash: dict[str, list[str]] = {}
        for p in sorted(paths):
            by_hash.setdefault(file_sha1(p), []).append(p)
        for group in by_hash.values():
            # Group is already sorted; keep group[0], delete the rest.
            for p in group[1:]:
                try:
                    os.remove(p)
                    deleted += 1
                    freed += size
                except OSError as e:
                    print(f"WARN: could not delete {p}: {e}", file=sys.stderr)

    # Sweep empty dirs left behind by the deletions.
    for dirpath, _dirs, filenames in os.walk(root, topdown=False):
        if dirpath == root:
            continue
        if not os.listdir(dirpath):
            try:
                os.rmdir(dirpath)
            except OSError:
                pass

    mb = freed / (1024 * 1024)
    print(f"  archives considered: {total_archives}")
    print(f"  duplicates deleted : {deleted} ({mb:.1f} MB freed)")
    print(f"  archives remaining : {total_archives - deleted}")


if __name__ == "__main__":
    main()
