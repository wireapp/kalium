#!/usr/bin/env python3
"""
Parse Maven POM <licenses> metadata into a TSV for downstream SBOM tooling.

For every *.pom under the given poms directory, resolve the artifact's
groupId/artifactId/version (falling back to <parent> for inherited fields) and
emit one row per artifact with its declared license names/URLs. A curated
override TSV takes precedence over POM-declared metadata when matched on
'groupId:artifactId'. Artifacts with neither an override nor a <licenses>
block are written to the no-licenses report — the punch list for the override
table.

Usage: parse-pom-licenses.py <poms_dir> <out_tsv> <out_nolic> <override_path>
"""

import csv
import glob
import os
import sys
import xml.etree.ElementTree as ET

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
    """Read the manual override TSV. Keyed by 'groupId:artifactId' — entries
    cover all versions of that artifact. Comments (#) and the header row are
    ignored; missing file is non-fatal."""
    table = {}
    if not os.path.isfile(path):
        print(f'  WARN: override table {path} not found; no overrides applied')
        return table
    with open(path) as f:
        for row in csv.reader(f, delimiter='\t'):
            if not row or not row[0] or row[0].lstrip().startswith('#'):
                continue
            if row[0] == 'groupId':
                continue
            if len(row) < 4:
                continue
            gid, aid, names, urls = (c.strip() for c in row[:4])
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
            hit = overrides.get(f'{gid}:{aid}')
            if hit is not None:
                from_override += 1
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


if __name__ == '__main__':
    main()
