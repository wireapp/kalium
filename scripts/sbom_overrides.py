"""Shared loaders/helpers for SBOM override metadata.

The override YAML (scripts/sbom-license-overrides.yaml) feeds two consumers:

  - parse-pom-licenses.py uses license_names/license_urls for Maven
    coordinates when emitting scan-pom-licenses.tsv;
  - generate-third-party-notice.py additionally uses notes and license_text,
    and consumes both Maven (groupId/artifactId) and npm (groupId='npm') rows.

Loading and stale-entry reporting live here so a schema change touches one file.
"""

import os
import sys

import yaml


def load_overrides(path):
    """Read sbom-license-overrides.yaml into
    {gid:aid: {names, urls, notes, license_text}}.

    Missing/malformed file is non-fatal — returns {} after a warning so a
    fresh checkout without overrides still runs.
    """
    table = {}
    if not path or not os.path.isfile(path):
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
        table[f'{gid}:{aid}'] = {
            'names': (entry.get('license_names') or '').strip(),
            'urls': (entry.get('license_urls') or '').strip(),
            'notes': (entry.get('notes') or '').strip(),
            # license_text intentionally NOT lstripped — multi-line YAML
            # literals encode meaningful indentation. Rendered with rstrip().
            'license_text': entry.get('license_text') or '',
        }
    return table


def report_unused_overrides(unused, override_path, *, kind):
    """Print an ERROR to stderr listing override keys that matched no
    artifact in the scan. `kind` ('Maven', 'npm') is the ecosystem label
    shown in the entry-count line.

    A no-op when `unused` is empty so callers don't need to guard.
    """
    if not unused:
        return
    suffix = 'y' if len(unused) == 1 else 'ies'
    print(
        f'  ERROR: {len(unused)} {kind} override entr{suffix} in '
        f'{override_path} matched no scanned artifact:',
        file=sys.stderr,
    )
    for k in unused:
        print(f'    - {k}', file=sys.stderr)
    print(
        '  Each entry above is either a typo or refers to a dependency '
        'no longer in the scan. Remove or correct it.',
        file=sys.stderr,
    )
