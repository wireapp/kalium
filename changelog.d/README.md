# Changelog Fragments

Add one Markdown fragment for each pull request that changes Kalium's public API,
ABI, or consumer-facing behavior.

Name fragments after the pull request or issue when possible:

```text
1234-added-backup-status-api.md
```

Keep fragments short and consumer-focused:

```markdown
### Added
- Added `BackupStatus.InProgress` for observing backup progress.

### Migration
No action required unless consumers exhaustively match `BackupStatus`.

### Compatibility
ABI: additive.
Source: additive.
Behavior: no behavior change.
```

The changelog gate runs when ABI dumps change or when a pull request has an
API-impacting label such as `api-impacting`, `public-api`, or the existing
`🚨 Potential breaking changes` label.

Use the `no-changelog-needed` label only when reviewers agree the change has no
consumer-facing release note. Use `internal-only` only when the ABI gate confirms
the published consumer surface did not change; it does not skip changelog
requirements when ABI dumps changed.
