#!/usr/bin/env python3
"""
Parse Maven POM <licenses> metadata into a TSV for downstream SBOM tooling.

For every *.pom under the given poms directory, resolve the artifact's
groupId/artifactId/version (falling back to <parent> for inherited fields) and
emit one row per artifact with its declared license names/URLs. A curated
override YAML takes precedence over POM-declared metadata when matched on
'groupId:artifactId'. Artifacts with neither an override nor a <licenses>
block are written to the no-licenses report — the punch list for the override
table.

Usage: parse-pom-licenses.py <poms_dir> <out_tsv> <out_nolic> <override_path>

Requires PyYAML. ScanCode-Toolkit pulls it in transitively, so when this
script is invoked via the venv set up by scripts/generate-sbom.sh it is
already importable.
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET

import yaml

NS = '{http://maven.apache.org/POM/4.0.0}'


def text(elem, tag):
    """Inner text of <tag> directly under elem, stripped. '' if absent."""
    if elem is None:
        return ''
    node = elem.find(NS + tag)
    if node is None or not node.text:
        return ''
    return node.text.strip()


def coord(root):
    """Resolve groupId/artifactId/version, falling back to <parent> for the
    inherited fields (common in POMs that omit groupId/version on the leaf)."""
    parent = root.find(NS + 'parent')
    gid = text(root, 'groupId') or text(parent, 'groupId')
    aid = text(root, 'artifactId')
    ver = text(root, 'version') or text(parent, 'version')
    return gid, aid, ver


def licenses(root):
    block = root.find(NS + 'licenses')
    if block is None:
        return []
    out = []
    for lic in block.findall(NS + 'license'):
        name = text(lic, 'name')
        url = text(lic, 'url')
        if name or url:
            out.append((name, url))
    return out


def load_overrides(path):
    """Read the manual override YAML. Keyed by 'groupId:artifactId' — entries
    cover all versions of that artifact. Returns (names, urls) per key; the
    `notes` field is consumed by generate-third-party-notice.py and ignored
    here. Missing file is non-fatal."""
    table = {}
    if not os.path.isfile(path):
        print(f'  WARN: override table {path} not found; no overrides applied')
        return table
    with open(path) as f:
        data = yaml.safe_load(f) or []
    if not isinstance(data, list):
        print(f'  WARN: {path} did not parse to a list of entries; no overrides applied')
        return table
    for entry in data:
        if not isinstance(entry, dict):
            continue
        gid = (entry.get('groupId') or '').strip()
        aid = (entry.get('artifactId') or '').strip()
        if not gid or not aid:
            continue
        names = (entry.get('license_names') or '').strip()
        urls = (entry.get('license_urls') or '').strip()
        table[f'{gid}:{aid}'] = (names, urls)
    return table


def main():
    if len(sys.argv) != 5:
        print(
            'Usage: parse-pom-licenses.py <poms_dir> <out_tsv> <out_nolic> <override_path>',
            file=sys.stderr,
        )
        sys.exit(2)
    poms_dir, out_tsv, out_nolic, override_path = sys.argv[1:5]

    overrides = load_overrides(override_path)
    pom_count = from_pom = from_override = unresolved = 0
    # Track which override keys were actually applied to a POM. After the loop,
    # any Maven override (`gid:aid` not starting with `npm:`) that never matched
    # points to a stale entry in the YAML — either a typo or a dependency that
    # was removed upstream. Surfaced as an ERROR so the operator notices.
    used_override_keys = set()
    with open(out_tsv, 'w') as tsv, open(out_nolic, 'w') as nolic:
        tsv.write('groupId\tartifactId\tversion\tlicense_names\tlicense_urls\tsource\n')
        for pom in sorted(glob.iglob(os.path.join(poms_dir, '**/*.pom'), recursive=True)):
            try:
                root = ET.parse(pom).getroot()
            except ET.ParseError:
                continue
            pom_count += 1
            gid, aid, ver = coord(root)
            # Override table wins over POM-declared metadata: it's curated and
            # canonical, the POM is upstream-declared (sometimes inconsistent or
            # ambiguous). Fall through to <licenses> only when no override matches.
            key = f'{gid}:{aid}'
            hit = overrides.get(key)
            if hit is not None:
                from_override += 1
                used_override_keys.add(key)
                names, urls = hit
                tsv.write(f'{gid}\t{aid}\t{ver}\t{names}\t{urls}\toverride\n')
                continue
            lics = licenses(root)
            if lics:
                from_pom += 1
                names = ' | '.join(n for n, _ in lics)
                urls = ' | '.join(u for _, u in lics)
                tsv.write(f'{gid}\t{aid}\t{ver}\t{names}\t{urls}\tpom\n')
            else:
                unresolved += 1
                nolic.write(f'{gid}:{aid}:{ver}\n')

    print(f'  Parsed {pom_count} POMs:')
    print(f'    {from_override} resolved via {override_path} (authoritative)')
    print(f'    {from_pom} fell through to POM <licenses>')
    print(f'    {unresolved} unresolved')
    print(f'  Wrote {out_tsv}')
    if unresolved:
        print(f'  Wrote {out_nolic} ({unresolved} entries — add to {override_path})')

    # Stale-override audit. Only Maven keys are this script's concern; npm
    # entries (groupId='npm') are validated by generate-third-party-notice.py
    # against the artifacts/npm/ tree it walks.
    maven_override_keys = {k for k in overrides if not k.startswith('npm:')}
    unused = sorted(maven_override_keys - used_override_keys)
    if unused:
        print(
            f'  ERROR: {len(unused)} Maven override entr{"y" if len(unused) == 1 else "ies"} '
            f'in {override_path} matched no scanned POM:',
            file=sys.stderr,
        )
        for k in unused:
            print(f'    - {k}', file=sys.stderr)
        print(
            '  Each entry above is either a typo or refers to a dependency '
            'no longer in the scan. Remove or correct it.',
            file=sys.stderr,
        )


if __name__ == '__main__':
    main()
