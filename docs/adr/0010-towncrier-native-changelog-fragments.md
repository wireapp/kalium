# 10. Towncrier-Native Consumer Changelog Fragments

Date: 2026-08-03

## Status

Draft

## Context

ADR 9 requires consumer-facing changelog fragments for public API, ABI, and behavior changes. Its
example fragment format used one Markdown file with internal `### Added`, `### Migration`, and
`### Compatibility` sections.

That format was easy to author but required custom parsing during release. The first collector
concatenated fragments directly, which produced duplicated section headings. It also rendered
compatibility as a detached section, making it unclear which compatibility note belonged to which
change.

The existing release workflow must remain a two-step process:

1. generate the Git changelog for the tag range
2. prepend curated consumer-facing changelog notes

## Decision

Use Towncrier to render consumer-facing changelog fragments, while keeping the generated Git
changelog as the second part of the release body.

Fragments will use native Towncrier filenames:

```text
changelog.d/1234-backup-status-api.added.md
changelog.d/1234-backup-status-api.migration.md
```

Supported suffixes are:

- `.added.md`
- `.changed.md`
- `.deprecated.md`
- `.removed.md`
- `.fixed.md`
- `.security.md`
- `.migration.md`

Do not use a separate `.compatibility.md` type. Compatibility information belongs inline under the
change it describes:

```markdown
Added `BackupStatus.InProgress` for observing backup progress.

  - ABI: additive
  - Source: additive
  - Behavior: no behavior change unless consumers exhaustively match `BackupStatus`.
```

The release wrapper should only select fragments for `BASE_REF...HEAD_REF`, run
`towncrier build --draft --version "$CURRENT_TAG"` in a temporary directory, and prepend the output
to the Git changelog. It should not parse changelog Markdown.

Towncrier is pinned in `scripts/changelog-requirements.txt` so Renovate can manage updates through
the `pip_requirements` manager.

## Consequences

**Easier:**

- Release-note grouping is handled by Towncrier instead of custom Markdown parsing.
- Repeated section headings are eliminated.
- Compatibility impact stays attached to the relevant change.
- The release body remains compatible with the existing workflow: consumer notes first, Git
  changelog second.
- Renovate can update the Towncrier dependency from a standard requirements file.

**More difficult:**

- A pull request may need more than one fragment file.
- Authors must use filename suffixes instead of internal `###` headings.
- Reviewers must check that ABI/source/behavior impact is included inline when relevant.
- Release CI now installs a small Python changelog toolchain.

This ADR supersedes only the changelog fragment format shown in ADR 9. ADR 9's API, ABI, review,
and changelog requirement policy remains in force.

## References

- [ADR 9: Public API, ABI, and Changelog Governance](0009-public-api-abi-and-changelog-governance.md)
- [Towncrier documentation](https://towncrier.readthedocs.io/)
