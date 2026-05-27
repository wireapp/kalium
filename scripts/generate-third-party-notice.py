#!/usr/bin/env python3
"""
Generate THIRD-PARTY-NOTICE.md from the SBOM artifact tree.

Two component streams feed the document:
  1. Maven coordinates from build/sbom/scan-pom-licenses.tsv (POM-declared
     license metadata, optionally overridden by
     scripts/sbom-license-overrides.yaml).
  2. npm packages discovered by walking artifacts/npm/**/package.json — the
     `license` / `licenses` field gives the SPDX identifier; the bundled
     LICENSE file (looked up via scan-license-files-found.txt) gives the
     verbatim text.

For each component the script emits a section with the verbatim LICENSE text
from the artifact's distribution when available, or a reference to the
canonical SPDX text in the document's appendix.

Entries in sbom-license-overrides.yaml may also carry a free-form `notes`
field. When present, the text renders inside the component's section after
the metadata bullets and before the bundled or canonical license text —
the slot the user sees when reading the notice top-to-bottom.

Long-tail SPDX coverage: rather than maintaining a hardcoded license-text
bundle, the appendix is populated from ScanCode-Toolkit's licensedcode
database (already a dependency of the surrounding generate-sbom.sh pipeline).
ScanCode ships canonical text for every SPDX license plus thousands of
non-SPDX variants, so adding a new dependency with an exotic license needs
no code change here.

Usage:
    python3 scripts/generate-third-party-notice.py BUILD_DIR

where BUILD_DIR is typically build/sbom/. Must be run with the venv's
python3 so that `licensedcode` and `yaml` are importable.
"""

import csv
import hashlib
import json
import os
import re
import sys
from collections import Counter, defaultdict

import yaml


def fail(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def parse_license_files_found(path):
    """Parse scan-license-files-found.txt into {package_basename: [paths]}.
    Paths are kept relative to the artifacts/ directory so they can be
    re-anchored at read time."""
    result = defaultdict(list)
    current = None
    with open(path) as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("## "):
                current = line[3:].strip()
            elif line.startswith("  ") and current:
                result[current].append(line.strip())
    return result


def candidate_basenames(aid, ver):
    """Maven artifacts unpack as <aid>-<ver>.<ext>-extract under
    artifacts/<bucket>/<module>/. Yield candidates in descending likelihood
    so the first hit wins."""
    for ext in ("jar", "aar", "klib", "war", "zip"):
        yield f"{aid}-{ver}.{ext}-extract"


def load_scancode_license_db():
    try:
        from licensedcode.cache import get_licenses_db
        return get_licenses_db()
    except ImportError:
        return None


# POM-dialect synonym table. ScanCode's licensedcode/data/licenses/*.yml only
# carries canonical names (e.g. "Apache License 2.0", "MIT License"); the
# well-known free-text synonyms ("The Apache Software License, Version 2.0",
# "GNU Lesser General Public License, Version 2.1") live in its rule engine,
# which is too heavy to invoke per-name. So we layer this table on top of
# the scancode-data index — it covers the variants that show up in Maven
# POMs in practice. Add an entry when a new name appears in the
# "Unresolved License Names" section of THIRD-PARTY-NOTICE.md.
POM_NAME_SYNONYMS = {
    # Apache 2.0 — most POMs in the JVM ecosystem
    "apache 2.0": "Apache-2.0",
    "apache-2.0": "Apache-2.0",
    "apache license 2.0": "Apache-2.0",
    "apache license, version 2.0": "Apache-2.0",
    "apache software license 2.0": "Apache-2.0",
    "apache software license, version 2.0": "Apache-2.0",
    "asl 2.0": "Apache-2.0",
    # MIT
    "mit": "MIT",
    "mit license": "MIT",
    # BSD family — "BSD License" alone is intentionally NOT mapped (ambiguous
    # between 2-clause and 3-clause; surface to manual review).
    "bsd 2-clause": "BSD-2-Clause",
    "bsd-2-clause": "BSD-2-Clause",
    "bsd 2-clause license": "BSD-2-Clause",
    "bsd 3-clause": "BSD-3-Clause",
    "bsd-3-clause": "BSD-3-Clause",
    "bsd 3-clause license": "BSD-3-Clause",
    "new bsd license": "BSD-3-Clause",
    "modified bsd license": "BSD-3-Clause",
    # GPL / LGPL
    "gpl-2.0": "GPL-2.0-only",
    "gpl-2.0-or-later": "GPL-2.0-or-later",
    "gpl-3.0": "GPL-3.0-only",
    "gpl-3.0-or-later": "GPL-3.0-or-later",
    "lgpl-2.1": "LGPL-2.1-only",
    "lgpl-2.1-or-later": "LGPL-2.1-or-later",
    "lgpl-3.0": "LGPL-3.0-only",
    "lgpl-3.0-or-later": "LGPL-3.0-or-later",
    "gnu lesser general public license, version 2.1": "LGPL-2.1-only",
    "gnu lesser general public license v2.1": "LGPL-2.1-only",
    "gnu general public license v2.0": "GPL-2.0-only",
    "gnu general public license v3.0": "GPL-3.0-only",
    # Eclipse
    "epl 1.0": "EPL-1.0",
    "epl 2.0": "EPL-2.0",
    "epl-1.0": "EPL-1.0",
    "epl-2.0": "EPL-2.0",
    "eclipse public license 1.0": "EPL-1.0",
    "eclipse public license 2.0": "EPL-2.0",
    "eclipse public license - v 1.0": "EPL-1.0",
    "eclipse public license - v 2.0": "EPL-2.0",
    "eclipse distribution license - v 1.0": "BSD-3-Clause",
    # Mozilla
    "mpl 1.1": "MPL-1.1",
    "mpl 2.0": "MPL-2.0",
    "mozilla public license 1.1": "MPL-1.1",
    "mozilla public license 2.0": "MPL-2.0",
    "mozilla public license, version 2.0": "MPL-2.0",
    # CDDL
    "cddl": "CDDL-1.0",
    "cddl 1.0": "CDDL-1.0",
    "cddl 1.1": "CDDL-1.1",
    "common development and distribution license 1.0": "CDDL-1.0",
    # ISC / CC0 / Unlicense / Boost
    "isc": "ISC",
    "isc license": "ISC",
    "cc0": "CC0-1.0",
    "cc0 1.0 universal": "CC0-1.0",
    "creative commons zero": "CC0-1.0",
    "the unlicense": "Unlicense",
    "unlicense": "Unlicense",
    "boost software license": "BSL-1.0",
    "boost software license 1.0": "BSL-1.0",
    "bsl-1.0": "BSL-1.0",
    # Misc that frequently appear
    "wtfpl": "WTFPL",
    "zlib": "Zlib",
    "zlib/libpng license": "Zlib",
}


def build_name_to_spdx_index(db):
    """Walk every License object in the DB and build a case-insensitive map
    from name/short_name/key/spdx_id to SPDX. Used as a fallback for names
    not in POM_NAME_SYNONYMS — gives us long-tail coverage for canonical
    names declared in POMs (e.g. when an upstream POM happens to write the
    exact ScanCode name like 'Apache License 2.0')."""
    idx = {}
    for _, lic in db.items():
        spdx = getattr(lic, "spdx_license_key", None)
        if not spdx:
            continue
        aliases = []
        for attr in ("name", "short_name", "key"):
            v = getattr(lic, attr, None)
            if v:
                aliases.append(v)
        aliases.append(spdx)
        for alias in aliases:
            k = " ".join(alias.strip().lower().split())
            if k and k not in idx:
                idx[k] = spdx
    return idx


def _candidates(name):
    """Yield normalized lookup keys for a free-text license name in priority
    order: exact (lowercased, whitespace-collapsed), then with leading
    'the ' stripped."""
    base = " ".join(name.lower().strip().split())
    if not base:
        return
    yield base
    if base.startswith("the "):
        yield base[4:]


def find_spdx_id(name, scancode_index):
    """Resolve a free-text license name to an SPDX ID. POM_NAME_SYNONYMS
    wins; scancode-data index is the fallback. Returns None for empty
    input and ambiguous names (e.g. plain 'BSD License')."""
    if not name or not name.strip():
        return None
    for c in _candidates(name):
        if c in POM_NAME_SYNONYMS:
            return POM_NAME_SYNONYMS[c]
    for c in _candidates(name):
        if c in scancode_index:
            return scancode_index[c]
    return None


def split_spdx_expression(expr):
    """Split a (possibly compound) SPDX expression into individual license
    tokens. Recognises OR/AND/WITH operators (case-insensitive) and strips
    surrounding parens. A plain identifier ('MIT') returns as a single-
    element list; an expression like '(MIT OR Apache-2.0)' yields two
    tokens. Compound expressions are common in npm package.json `license`
    fields and occasionally appear in Maven POMs."""
    if not expr:
        return []
    s = expr.strip().strip("()").strip()
    if not s:
        return []
    parts = re.split(r"\s+(?:OR|AND|WITH)\s+", s, flags=re.IGNORECASE)
    return [p.strip().strip("()").strip() for p in parts if p.strip()]


def resolve_licenses(names_raw, name_to_spdx):
    """Resolve a license name string to (spdx_ids, unresolved_names).
    Composes two layers of splitting so it works for both Maven's pipe-
    separated names ('LGPL-2.1 | EPL-2.0') and SPDX expressions within
    each name ('MIT OR Apache-2.0'). '(MIT OR Apache-2.0) | EPL-2.0'
    therefore yields three tokens."""
    spdx_ids = []
    unresolved = set()
    for outer in names_raw.split(" | "):
        outer = outer.strip()
        if not outer:
            continue
        for token in split_spdx_expression(outer):
            spdx = find_spdx_id(token, name_to_spdx)
            if spdx:
                if spdx not in spdx_ids:
                    spdx_ids.append(spdx)
            else:
                unresolved.add(token)
    return spdx_ids, unresolved


def load_license_overrides(path):
    """Load scripts/sbom-license-overrides.yaml into
    {gid:aid: {names, urls, notes}}.

    Shared with scripts/parse-pom-licenses.py via the same YAML file (that
    script ignores the notes field; this one renders it). Maven rows use a
    real groupId; npm rows use groupId='npm' and the package name (or
    @scope/name) as artifactId, so Maven and npm keys live in disjoint
    spaces and don't collide. A missing file is non-fatal."""
    table = {}
    if not path or not os.path.isfile(path):
        return table
    with open(path) as f:
        data = yaml.safe_load(f) or []
    if not isinstance(data, list):
        return table
    for entry in data:
        if not isinstance(entry, dict):
            continue
        gid = (entry.get("groupId") or "").strip()
        aid = (entry.get("artifactId") or "").strip()
        if not gid or not aid:
            continue
        table[f"{gid}:{aid}"] = {
            "names": (entry.get("license_names") or "").strip(),
            "urls": (entry.get("license_urls") or "").strip(),
            "notes": (entry.get("notes") or "").strip(),
            # license_text intentionally NOT stripped of leading whitespace
            # in the value — multi-line YAML literals encode indentation
            # that consumers may want to preserve. Only trailing whitespace
            # is removed (handled at render time).
            "license_text": entry.get("license_text") or "",
        }
    return table


def normalize_npm_license(field):
    """Coerce a package.json `license` / `licenses` field into the same
    pipe-separated form ('A | B') the Maven flow uses for names/urls.
    Handles plain strings, {type, url} objects, and arrays of either.
    Returns ('', '') for empty or unrecognised inputs."""
    if not field:
        return "", ""
    if isinstance(field, str):
        return field, ""
    if isinstance(field, dict):
        return field.get("type", "") or "", field.get("url", "") or ""
    if isinstance(field, list):
        names, urls = [], []
        for item in field:
            if isinstance(item, dict):
                t = item.get("type")
                u = item.get("url")
                if t:
                    names.append(t)
                if u:
                    urls.append(u)
            elif isinstance(item, str):
                names.append(item)
        return " | ".join(names), " | ".join(urls)
    return "", ""


# Prefixes marking first-party Kotlin/JS packages: the Kotlin Gradle plugin
# emits one npm-style package per Kalium module (e.g. `kalium-core-common`,
# `@wireapp/kalium-backup`) into the resolved node_modules tree alongside
# real third-party deps. Those are *our own* code and don't belong in a
# third-party notice — this is the npm analog of Sbom.kt's
# `componentFilter { id -> id !is ProjectComponentIdentifier }` for Maven.
FIRST_PARTY_NPM_PREFIXES = ("kalium-", "@wireapp/kalium-")


def is_first_party_npm(name):
    return any(name.startswith(p) for p in FIRST_PARTY_NPM_PREFIXES)


def npm_name_likely_duplicates_maven(name, maven_aids):
    """True if an npm package looks like a Kotlin/JS-emitted variant of a
    Maven coordinate already covered on the Maven side of the notice.

    The Kotlin Gradle plugin publishes KMP libraries as npm packages whose
    name is either the bare Maven artifactId (`core-crypto-kmp`, `icu4j`)
    or `<gradle-project>-<artifactId>` (`ktor-ktor-client-core`,
    `Kotlin-DateTime-library-kotlinx-datetime`). Their package.json
    typically declares no license, so they'd otherwise land as
    'Verify manually' duplicates of fully-attributed Maven sections.

    Conservative: exact match, or trailing match against a multi-segment
    artifactId (single-segment IDs like 'core' are too generic to anchor
    a suffix match on)."""
    if name in maven_aids:
        return True
    for aid in maven_aids:
        if "-" in aid and name.endswith("-" + aid):
            return True
    return False


WORKSPACE_MANIFESTS_SUBDIR = "_workspace_manifests"


def _read_package_json(path):
    """Load a package.json file; return None on read or parse error."""
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError):
        return None


def compute_runtime_closure(artifacts_dir):
    """Compute the runtime-only npm package closure for the customer
    deliverable. Returns the set of package names safe to include;
    anything outside is build/test-time tooling (mocha, webpack,
    typescript, chokidar, ...) that doesn't ship to customers.

    Algorithm:
      1. Seeds: union of `dependencies` + `peerDependencies` from every
         non-test workspace package.json under
         `artifacts/npm/_workspace_manifests/`.
      2. BFS the forward dep graph built from every other package.json
         in `artifacts/npm/`, using each package's `dependencies` +
         `peerDependencies`.

    Returns None when the manifests subtree is absent — old artifact
    trees from before Sbom.kt started copying workspace manifests should
    keep generating notices unchanged (no filtering applied)."""
    npm_root = os.path.join(artifacts_dir, "npm")
    manifests_dir = os.path.join(npm_root, WORKSPACE_MANIFESTS_SUBDIR)
    if not os.path.isdir(manifests_dir):
        return None

    def runtime_deps(pj):
        out = set(pj.get("dependencies") or {})
        out.update(pj.get("peerDependencies") or {})
        return out

    # Forward dep graph from every non-meta package.json in artifacts/npm/.
    forward = {}
    for root, dirs, files in os.walk(npm_root):
        if WORKSPACE_MANIFESTS_SUBDIR in root.split(os.sep):
            continue
        if "package.json" not in files:
            continue
        pj = _read_package_json(os.path.join(root, "package.json"))
        if not pj:
            continue
        name = pj.get("name")
        if not name:
            continue
        forward.setdefault(name, set()).update(runtime_deps(pj))

    # Seeds: non-test workspace dependencies.
    seeds = set()
    for root, _dirs, files in os.walk(manifests_dir):
        if "package.json" not in files:
            continue
        pj = _read_package_json(os.path.join(root, "package.json"))
        if not pj:
            continue
        name = pj.get("name", "")
        if not name or name.endswith("-test"):
            continue
        seeds.update(runtime_deps(pj))

    closure = set()
    queue = list(seeds)
    while queue:
        pkg = queue.pop()
        if pkg in closure:
            continue
        closure.add(pkg)
        queue.extend(forward.get(pkg, set()))
    return closure


def discover_npm_packages(artifacts_dir):
    """Walk artifacts/npm/ and yield one dict per discovered package.json:
    {name, version, license_names, license_urls, dir, key}.

    First-party Kalium npm packages (Kotlin/JS module outputs) are filtered
    out; see FIRST_PARTY_NPM_PREFIXES. The workspace-manifests subtree (used
    by compute_runtime_closure) is also skipped to avoid duplicate entries.

    `key` mirrors the bash audit's dedup-key derivation
    (scripts/generate-sbom.sh: the path segment after the LAST
    'node_modules/', preserving @scope/name for scoped packages). It's
    the lookup into scan-license-files-found.txt for the bundled
    LICENSE/NOTICE files."""
    npm_root = os.path.join(artifacts_dir, "npm")
    if not os.path.isdir(npm_root):
        return
    for root, _dirs, files in os.walk(npm_root):
        if WORKSPACE_MANIFESTS_SUBDIR in root.split(os.sep):
            continue
        if "package.json" not in files:
            continue
        try:
            with open(os.path.join(root, "package.json"), encoding="utf-8") as f:
                pj = json.load(f)
        except (OSError, json.JSONDecodeError):
            continue
        name = pj.get("name")
        version = pj.get("version")
        if not name or not version:
            continue
        if is_first_party_npm(name):
            continue
        rel = os.path.relpath(root, npm_root)
        if "node_modules/" in rel:
            key = rel.rsplit("node_modules/", 1)[1]
        else:
            key = name
        names, urls = normalize_npm_license(pj.get("license") or pj.get("licenses"))
        yield {
            "name": name,
            "version": version,
            "license_names": names,
            "license_urls": urls,
            "dir": root,
            "key": key,
        }


def read_file_text(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError as e:
        return f"(unable to read: {e})"


def spdx_anchor(spdx):
    """GitHub-flavored markdown anchor for an SPDX ID."""
    return "license-" + re.sub(r"[^a-z0-9-]", "-", spdx.lower())


def main():
    if len(sys.argv) not in (2, 3):
        fail(f"Usage: {sys.argv[0]} BUILD_DIR [OUTPUT_MD]")
    build_dir = sys.argv[1].rstrip("/")
    pom_tsv = os.path.join(build_dir, "scan-pom-licenses.tsv")
    found_txt = os.path.join(build_dir, "scan-license-files-found.txt")
    artifacts_dir = os.path.join(build_dir, "artifacts")
    output_md = sys.argv[2] if len(sys.argv) == 3 else os.path.join(build_dir, "THIRD-PARTY-NOTICE.md")

    for required in (pom_tsv, found_txt):
        if not os.path.isfile(required):
            fail(f"missing input file: {required}")
    if not os.path.isdir(artifacts_dir):
        fail(f"missing artifacts directory: {artifacts_dir}")

    db = load_scancode_license_db()
    if db is None:
        fail(
            "ScanCode's licensedcode module is not importable from this "
            "Python. Run with .venv/bin/python3 (or reinstall scancode-toolkit)."
        )
    name_to_spdx = build_name_to_spdx_index(db)
    found_map = parse_license_files_found(found_txt)

    # Load manual license overrides. Same YAML as parse-pom-licenses.py uses
    # for Maven coords; this script also consumes the npm rows
    # (groupId='npm') plus the per-entry `notes` field for both ecosystems.
    override_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "sbom-license-overrides.yaml"
    )
    overrides = load_license_overrides(override_path)

    # Runtime closure: set of npm package names reachable from non-test
    # workspace `dependencies`/`peerDependencies`. Filters out test/build-
    # time tooling (mocha, webpack, typescript and their transitives).
    # None means the workspace manifests aren't present — skip the filter
    # so older artifact trees still generate (possibly over-inclusive)
    # notices instead of breaking.
    runtime_closure = compute_runtime_closure(artifacts_dir)

    spdx_used = set()
    unresolved_names = set()
    sections = []
    # Maven artifactIds; populated during the Maven loop and used by the
    # npm-side `npm_name_likely_duplicates_maven` heuristic.
    maven_aids = set()

    # --- Maven coordinates ---
    with open(pom_tsv) as f:
        for row in csv.DictReader(f, delimiter="\t"):
            gid = row["groupId"]
            aid = row["artifactId"]
            ver = row["version"]
            maven_aids.add(aid)
            names_raw = row["license_names"]
            urls_raw = row["license_urls"]
            source = row["source"]

            # Locate verbatim LICENSE files for this coordinate by trying each
            # candidate artifact extension. First hit wins; a single Maven
            # coordinate produces one extracted directory.
            verbatim = []
            for basename in candidate_basenames(aid, ver):
                rels = found_map.get(basename)
                if rels:
                    for rel in rels:
                        full = os.path.join(artifacts_dir, rel)
                        if os.path.isfile(full):
                            verbatim.append((rel, read_file_text(full)))
                    break

            spdx_ids, this_unresolved = resolve_licenses(names_raw, name_to_spdx)
            spdx_used.update(spdx_ids)
            unresolved_names.update(this_unresolved)

            # Notes and license_text are sourced from the YAML overrides
            # file regardless of whether the Maven row's source is 'override'
            # or 'pom' — an operator might want either attached to a
            # coordinate while still letting the upstream POM declare the
            # license itself.
            override_entry = overrides.get(f"{gid}:{aid}") or {}
            notes = override_entry.get("notes", "")
            license_text = override_entry.get("license_text", "")

            coord = f"{gid}:{aid}:{ver}"
            sections.append({
                "kind": "maven",
                "header": coord,
                "sort_key": (0, coord.lower()),
                "names": names_raw,
                "urls": urls_raw,
                "source": source,
                "spdx_ids": spdx_ids,
                "verbatim": verbatim,
                "notes": notes,
                "license_text": license_text,
            })

    # --- npm packages ---
    # Dedup by (name, version): the same package can land under multiple
    # JS compilations' node_modules trees. Different versions of the same
    # name remain as separate sections — each is a distinct licensable unit.
    npm_seen = set()
    for pkg in discover_npm_packages(artifacts_dir):
        dedup = (pkg["name"], pkg["version"])
        if dedup in npm_seen:
            continue
        npm_seen.add(dedup)

        # Manual override wins over every filter below — an explicit override
        # is the operator saying 'I've verified this package, include it'.
        # `notes` and `license_text` may be set whether or not the override
        # replaces license metadata, so they're read independently of the
        # source branch.
        override = overrides.get(f"npm:{pkg['name']}")
        notes = (override or {}).get("notes", "")
        license_text = (override or {}).get("license_text", "")
        if override is not None:
            license_names = override["names"]
            license_urls = override["urls"]
            source = "override"
        else:
            # Build/test-time filter: only include packages reachable from
            # the runtime closure of non-test workspace dependencies.
            # Filters out mocha, webpack, typescript, chokidar, ajv etc.
            # and their transitives.
            if runtime_closure is not None and pkg["name"] not in runtime_closure:
                continue
            # Skip KMP-as-npm duplicates: Kotlin/JS emits a license-less npm
            # variant of every KMP Maven dep already covered on the Maven
            # side. Without a license field these would otherwise render as
            # 'Verify manually' duplicates of fully-attributed sections.
            if not pkg["license_names"] and npm_name_likely_duplicates_maven(
                pkg["name"], maven_aids
            ):
                continue
            license_names = pkg["license_names"]
            license_urls = pkg["license_urls"]
            source = "package.json"

        verbatim = []
        rels = found_map.get(pkg["key"])
        if rels:
            for rel in rels:
                full = os.path.join(artifacts_dir, rel)
                if os.path.isfile(full):
                    verbatim.append((rel, read_file_text(full)))

        spdx_ids, this_unresolved = resolve_licenses(license_names, name_to_spdx)
        spdx_used.update(spdx_ids)
        unresolved_names.update(this_unresolved)

        header = f"npm:{pkg['name']}@{pkg['version']}"
        sections.append({
            "kind": "npm",
            "header": header,
            "sort_key": (1, header.lower()),
            "names": license_names,
            "urls": license_urls,
            "source": source,
            "spdx_ids": spdx_ids,
            "verbatim": verbatim,
            "notes": notes,
            "license_text": license_text,
        })

    sections.sort(key=lambda s: s["sort_key"])

    # Verbatim-body deduplication. Bundled LICENSE/NOTICE text and
    # license_text overrides are commonly identical across many components
    # (the Apache-2.0 boilerplate is the worst offender — every Apache
    # project ships the same LICENSE text). When the same body appears in
    # two or more component sections, factor it out into a single
    # "Bundled License Texts" appendix entry and replace each inline
    # <details> block with a link-note pointing at the appendix anchor.
    #
    # Threshold is "appears in >= 2 distinct sections" — a one-off body
    # stays inline, since extracting it would only add appendix noise.
    # Counts are by section (a single section listing the same body
    # twice — e.g. both LICENSE and LICENSE.txt in one jar — counts once).
    body_section_counts = Counter()
    for s in sections:
        in_this_section = set()
        for _, text in s["verbatim"]:
            key = text.strip()
            if key:
                in_this_section.add(key)
        lt = (s.get("license_text") or "").strip()
        if lt:
            in_this_section.add(lt)
        for key in in_this_section:
            body_section_counts[key] += 1

    dedup_index = {}  # stripped body -> {anchor, original, spdx_hint, packages}
    for s in sections:
        contributions = [(text, s.get("spdx_ids") or []) for _, text in s["verbatim"]]
        if s.get("license_text"):
            contributions.append((s["license_text"], s.get("spdx_ids") or []))
        seen_in_section = set()
        for original, spdx_ids in contributions:
            key = original.strip()
            if not key or body_section_counts[key] < 2:
                continue
            if key in seen_in_section:
                continue
            seen_in_section.add(key)
            entry = dedup_index.get(key)
            if entry is None:
                anchor = "bundled-license-" + hashlib.sha256(
                    key.encode("utf-8")
                ).hexdigest()[:12]
                entry = {
                    "anchor": anchor,
                    "original": original.rstrip(),
                    "spdx_hint": spdx_ids[0] if spdx_ids else "",
                    "packages": [],
                }
                dedup_index[key] = entry
            if s["header"] not in entry["packages"]:
                entry["packages"].append(s["header"])

    # SPDX -> License lookup for the canonical-text appendix.
    by_spdx = {}
    for _, lic in db.items():
        spdx = getattr(lic, "spdx_license_key", None)
        if spdx and spdx not in by_spdx:
            by_spdx[spdx] = lic

    maven_count = sum(1 for s in sections if s["kind"] == "maven")
    npm_count = sum(1 for s in sections if s["kind"] == "npm")
    # Count any section that ships an inline license body — bundled
    # LICENSE files from the distribution OR a license_text override pasted
    # via sbom-license-overrides.yaml. Both render as a <details> block.
    with_verbatim = sum(
        1 for s in sections if s["verbatim"] or s.get("license_text")
    )
    with open(output_md, "w") as out:
        out.write("# Third-Party Notices\n\n")
        out.write(
            "This software includes the following third-party components, "
            "each subject to its respective license terms.\n\n"
        )
        out.write(
            "Each section below identifies a component (Maven coordinate "
            "`groupId:artifactId:version` or npm package `npm:name@version`), "
            "its declared license, and either the verbatim LICENSE/NOTICE "
            "text bundled in the package's distribution (collapsed by "
            "default — click to expand) or a reference to the canonical "
            "license text in the [License Texts](#license-texts) appendix. "
            "When the same verbatim license body is bundled by multiple "
            "components, it is reproduced once in the "
            "[Bundled License Texts](#bundled-license-texts) appendix and "
            "linked from each component section.\n\n"
        )
        out.write(
            f"Generated from {len(sections)} components "
            f"({maven_count} Maven, {npm_count} npm) — "
            f"{with_verbatim} ship a bundled LICENSE/NOTICE file, "
            f"{len(sections) - with_verbatim} reference the appendix.\n\n"
        )
        out.write("---\n\n## Components\n\n")
        for s in sections:
            out.write(f"### {s['header']}\n\n")
            out.write(f"- **License:** {s['names']}\n")
            if s["urls"]:
                out.write(f"- **URL:** {s['urls']}\n")
            if s["spdx_ids"]:
                refs = ", ".join(
                    f"[{spdx}](#{spdx_anchor(spdx)})" for spdx in s["spdx_ids"]
                )
                out.write(f"- **SPDX:** {refs}\n")
            out.write(f"- **Source:** `{s['source']}`\n\n")

            # Free-form notes from sbom-license-overrides.yaml render here —
            # after the structured metadata, before any bundled or canonical
            # license text. Multi-line YAML literals (`notes: |`) come
            # through as already-formatted paragraphs.
            if s.get("notes"):
                out.write(f"**Notes:**\n\n{s['notes'].strip()}\n\n")

            # When a verbatim body has been factored out into the Bundled
            # License Texts appendix (because >= 2 sections share it),
            # emit a single link-note pointing at the anchor in place of
            # the inline <details> block. `section_anchors_emitted` keeps
            # the note from repeating when the same section bundles the
            # same body under multiple filenames.
            section_anchors_emitted = set()

            for fname, text in s["verbatim"]:
                key = text.strip()
                if key in dedup_index:
                    anchor = dedup_index[key]["anchor"]
                    if anchor not in section_anchors_emitted:
                        out.write(
                            f"[The full text of this license is included "
                            f"at the end of this file.](#{anchor})\n\n"
                        )
                        section_anchors_emitted.add(anchor)
                else:
                    out.write(
                        f"<details>\n<summary>Bundled <code>{fname}</code>"
                        f"</summary>\n\n"
                    )
                    out.write("```\n")
                    out.write(text.rstrip())
                    out.write("\n```\n\n</details>\n\n")

            # license_text override — operator-pasted license body for
            # packages that ship no LICENSE file in their distribution.
            # Rendered with the same <details> shell as bundled files so
            # the customer-facing UX is uniform — and subject to the same
            # dedup factoring above.
            if s.get("license_text"):
                key = s["license_text"].strip()
                if key in dedup_index:
                    anchor = dedup_index[key]["anchor"]
                    if anchor not in section_anchors_emitted:
                        out.write(
                            f"[The full text of this license is included "
                            f"at the end of this file.](#{anchor})\n\n"
                        )
                        section_anchors_emitted.add(anchor)
                else:
                    out.write(
                        "<details>\n<summary>License text (from override)"
                        "</summary>\n\n```\n"
                    )
                    out.write(s["license_text"].rstrip())
                    out.write("\n```\n\n</details>\n\n")

            if not s["verbatim"] and not s.get("license_text") and not s["spdx_ids"]:
                out.write(
                    "> **No bundled LICENSE file** and license name could not "
                    "be normalized to an SPDX identifier. Verify manually.\n\n"
                )

            out.write("---\n\n")

        out.write("## License Texts\n\n")
        out.write(
            "Canonical text for each license referenced above. Sourced from "
            "ScanCode-Toolkit's bundled license database.\n\n"
        )
        for spdx in sorted(spdx_used):
            lic = by_spdx.get(spdx)
            if not lic:
                continue
            text = (getattr(lic, "text", "") or "").strip()
            anchor = spdx_anchor(spdx)
            name = getattr(lic, "name", "") or ""
            out.write(f"### {spdx}\n")
            out.write(f'<a id="{anchor}"></a>\n\n')
            if name:
                out.write(f"*{name}*\n\n")
            out.write("```\n")
            out.write(text if text else "(canonical text unavailable)")
            out.write("\n```\n\n")

        if dedup_index:
            out.write("## Bundled License Texts\n")
            out.write('<a id="bundled-license-texts"></a>\n\n')
            out.write(
                "Verbatim license and notice texts that are bundled "
                "identically by multiple components above. Each body is "
                "reproduced once here and linked from every component "
                "section that ships it.\n\n"
            )
            # Order entries by SPDX hint (so identically-classified bodies
            # sit together), then by descending fan-out (most-referenced
            # first — usually what the reader cares about), then by the
            # stable hash anchor as a final tiebreaker.
            ordered = sorted(
                dedup_index.values(),
                key=lambda e: (
                    e["spdx_hint"] or "￿",
                    -len(e["packages"]),
                    e["anchor"],
                ),
            )
            for i, entry in enumerate(ordered, start=1):
                title = f"Bundled license body {i}"
                if entry["spdx_hint"]:
                    title += f" — {entry['spdx_hint']}"
                out.write(f"### {title}\n")
                out.write(f'<a id="{entry["anchor"]}"></a>\n\n')
                out.write(
                    f"Referenced by {len(entry['packages'])} components:\n\n"
                )
                for pkg in entry["packages"]:
                    out.write(f"- `{pkg}`\n")
                out.write("\n```\n")
                out.write(entry["original"])
                out.write("\n```\n\n")

        if unresolved_names:
            out.write("## Unresolved License Names\n\n")
            out.write(
                "The following license names could not be normalized to SPDX "
                "identifiers. The packages declaring them are listed in their "
                "individual sections above and need manual verification before "
                "shipping this notice.\n\n"
            )
            for nm in sorted(unresolved_names):
                out.write(f"- `{nm}`\n")

    print(f"  Wrote {output_md}")
    print(f"    {len(sections)} components ({maven_count} Maven, {npm_count} npm)")
    print(f"    {with_verbatim} include verbatim LICENSE/NOTICE text")
    print(f"    {len(spdx_used)} distinct SPDX licenses in the appendix")
    if dedup_index:
        dedup_refs = sum(len(e["packages"]) for e in dedup_index.values())
        print(
            f"    {len(dedup_index)} verbatim bodies deduplicated into the "
            f"Bundled License Texts appendix ({dedup_refs} references)"
        )
    if unresolved_names:
        print(
            f"    {len(unresolved_names)} unresolved license names "
            "(see end of doc — manual review needed)"
        )


if __name__ == "__main__":
    main()
