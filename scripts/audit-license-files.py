#!/usr/bin/env python3
"""
Audit LICENSE/NOTICE/COPYING file presence per third-party package.

Walks the SBOM artifact tree, dedups occurrences of the same upstream
package across per-module copies, and writes:
  - scan-license-files-found.txt   (per-package list of bundled license files;
                                    consumed by generate-third-party-notice.py)
  - scan-license-files-missing.txt (one dedup-key per line, sorted; the
                                    punch list for sbom-license-overrides.yaml)

Discovery sources:
  1. Top-level *-extract directories (an extracted jar/aar/klib/zip/war).
     Nested *-extract (e.g. classes.jar-extract inside an .aar-extract) is
     part of its parent package, not a separately-licensed unit.
  2. Every directory containing a package.json under artifacts/npm/.
  3. Prebuilt AVS native libs as one unit.

Dedup keys live in disjoint namespaces — '*-extract' basenames, npm
package names (with @scope/name preserved), and the literal 'native/avs' —
so cross-source collisions cannot occur. When the same key surfaces from
multiple paths, the lexicographically smallest path wins (matches the
predecessor bash `sort | awk '!seen[$1]++'`).

Usage: audit-license-files.py <artifacts_dir> <output_dir>
"""

import os
import sys

# Case-insensitive prefix match. Catches LICENSE, LICENSE.txt, LICENCE-MIT,
# NOTICE.md, COPYING.LESSER, UNLICENSE, etc. Prefix (not substring) keeps
# false positives like "JLicenseManager.kt" out — a false positive here
# would silently drop a notice from the customer report, which is worse
# than over-reporting misses.
LICENSE_FILE_PREFIXES = ("license", "licence", "notice", "copying", "unlicense", "copyright", "attribution", 
                         "legal", "third-party-notice", "third_party_notice", "third-party-license",
                         "third_party_license", "AL20", "AL2", "apache", "MIT", "GPL", "LGPL", "BSD", 
                         "MPL", "CDDL", "EPL", "EULA")


def is_license_file(name):
    lower = name.lower()
    return any(lower.startswith(p) for p in LICENSE_FILE_PREFIXES)


def discover_packages(artifacts_dir):
    """Yield (canonical_path,) for every package occurrence. Caller derives
    the dedup key and resolves duplicates."""
    npm_root = os.path.join(artifacts_dir, "npm")
    avs_root = os.path.join(artifacts_dir, "native", "avs")

    # 1) Top-level *-extract dirs. Prune the subtree as we yield each one so
    #    nested *-extract (e.g. classes.jar-extract inside an .aar-extract)
    #    isn't reported as a separate package.
    for dirpath, dirnames, _ in os.walk(artifacts_dir):
        for d in list(dirnames):
            if d.endswith("-extract"):
                yield os.path.join(dirpath, d)
                dirnames.remove(d)

    # 2) Every directory under artifacts/npm/ containing a package.json.
    #    Includes _workspace_manifests/* entries (which typically have no
    #    LICENSE and end up in the missing list — harmless, since the notice
    #    generator skips them when looking up verbatim text).
    if os.path.isdir(npm_root):
        for dirpath, _dirs, files in os.walk(npm_root):
            if "package.json" in files:
                yield dirpath

    # 3) Prebuilt AVS native libs — opaque blob, audited as one unit.
    if os.path.isdir(avs_root):
        yield avs_root


def dedup_key(rel):
    """Mirror of the bash audit's `case "$rel"` block.

    AVS collapses to one entry; npm packages with a node_modules ancestor
    use everything after the LAST node_modules/ (so deeply nested
    transitives `foo/node_modules/bar/node_modules/baz` collapse to `baz`
    and @scope/name is preserved); *-extract dirs key by basename; anything
    else (workspace manifests, raw npm artifacts without node_modules)
    keys by full relative path.
    """
    if rel == "native/avs" or rel.startswith("native/avs/"):
        return "native/avs"
    # Match the bash `npm/*/node_modules/*` glob: require at least one
    # component between `npm/` and `/node_modules/` (rules out a literal
    # `npm/node_modules/...`, which the bash pattern also rejects).
    if rel.startswith("npm/") and "/node_modules/" in rel[len("npm/"):]:
        return rel.rsplit("/node_modules/", 1)[1]
    if rel.endswith("-extract"):
        return os.path.basename(rel)
    return rel


def find_license_files(pkg_dir):
    matches = []
    for dirpath, _dirs, files in os.walk(pkg_dir):
        for name in files:
            if is_license_file(name):
                matches.append(os.path.join(dirpath, name))
    matches.sort()
    return matches


def main():
    if len(sys.argv) != 3:
        print(
            "Usage: audit-license-files.py <artifacts_dir> <output_dir>",
            file=sys.stderr,
        )
        sys.exit(2)
    artifacts_dir, output_dir = sys.argv[1], sys.argv[2]

    occurrences = list(discover_packages(artifacts_dir))

    # Resolve duplicates: lexicographic sort, keep first path per key.
    # Tuple (key, path) sort gives the same order as bash's `sort` on
    # tab-separated "key\tpath" lines because tab < any printable char.
    pairs = sorted(
        (dedup_key(os.path.relpath(p, artifacts_dir)), p) for p in occurrences
    )
    canonical = {}
    for key, path in pairs:
        canonical.setdefault(key, path)

    found_path = os.path.join(output_dir, "scan-license-files-found.txt")
    missing_path = os.path.join(output_dir, "scan-license-files-missing.txt")

    found_count = missing_count = 0
    with open(found_path, "w") as found_f, open(missing_path, "w") as missing_f:
        for key in sorted(canonical):
            matches = find_license_files(canonical[key])
            if matches:
                found_count += 1
                found_f.write(f"## {key}\n")
                for m in matches:
                    found_f.write(f"  {os.path.relpath(m, artifacts_dir)}\n")
                found_f.write("\n")
            else:
                missing_count += 1
                missing_f.write(f"{key}\n")

    total = found_count + missing_count
    print(
        f"  Audited {total} unique packages "
        f"(from {len(occurrences)} artifact occurrences):"
    )
    print(f"    {found_count} have a LICENSE/NOTICE/COPYING file")
    print(f"    {missing_count} do not (see {missing_path})")
    print(f"  Wrote {found_path}")


if __name__ == "__main__":
    main()
