# Changelog Fragments

Add one or more Towncrier Markdown fragments for each pull request that changes
Kalium's public API, ABI, or consumer-facing behavior.

Name fragments after the pull request, issue, or changed feature when possible.
The filename suffix controls the release-note section:

```text
1234-backup-status-api.added.md
```

Supported suffixes:

- `.added.md`
- `.changed.md`
- `.deprecated.md`
- `.removed.md`
- `.fixed.md`
- `.security.md`

Keep fragments short and consumer-focused. Put compatibility and migration
details inline with the change they describe instead of creating separate
compatibility or migration fragments:

```markdown
Added `BackupStatus.InProgress` for observing backup progress.

  - ABI: additive
  - Source: additive
  - Behavior: no behavior change unless consumers exhaustively match `BackupStatus`.
  - Migration: no action required unless consumers exhaustively match `BackupStatus`.
```

The changelog gate runs when ABI dumps change or when a pull request has an
API-impacting label such as `api-impacting`, `public-api`, or the existing
`🚨 Potential breaking changes` label.

Use the `no-changelog-needed` label only when reviewers agree the change has no
consumer-facing release note. The `internal-only` label documents scope but does
not skip this changelog gate when public API/ABI release notes are required.
