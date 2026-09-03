---
name: upstream-commit-migrator
description: "Adapt exactly the next first-parent commit from wireapp/kalium develop into Super Kalium and open one single-commit PR with upstream author credit. Use when asked to sync, port, or migrate the next upstream Kalium commit. Do not use for bulk rebases or unrelated cherry-picks."
---

# Upstream commit migrator

Port one upstream `develop` commit at a time. Preserve its behavior, but fit the implementation to Super Kalium's iOS work and accepted ADRs.

## Repository contract

- Downstream repository: `https://github.com/wireapp/super-kalium.git`.
- Upstream repository: `https://github.com/wireapp/kalium.git`.
- Base branch: `develop` in both repositories.
- Checkpoint: `UPSTREAM_KALIUM_COMMIT.txt` at the repository root. It contains one full lowercase commit SHA and a trailing newline.
- Commit order: first-parent order on upstream `develop`, starting after the checkpoint.
- Delivery: one upstream commit becomes one downstream commit in one non-draft PR.
- Lock: never work on another upstream commit while any open Super Kalium PR contains the `Upstream-Commit:` marker or uses the `mo/upstream-` branch prefix.

The migration PR advances the checkpoint to its upstream SHA. Until that PR merges, `develop` keeps the old checkpoint. If the PR closes without merging, the same upstream commit remains next.

## Pass the gates before editing

1. Fetch `origin/develop` and upstream `develop`. Use an existing `upstream` remote when it points to `wireapp/kalium`. Otherwise fetch the URL into a local remote-tracking ref such as `refs/remotes/kalium/develop`. Do not change a configured remote that points somewhere else.
2. Read the checkpoint from the fetched `origin/develop`, not from a stale feature branch. Require exactly 40 hexadecimal characters. Confirm that the checkpoint is an ancestor of fetched upstream `develop`. Stop if either check fails.
3. Query open PRs in `wireapp/super-kalium` with `gh pr list`. Inspect their bodies and head branches. If a body contains `Upstream-Commit:` or a head branch starts with `mo/upstream-`, report the PR's number, title, and URL, then stop. Do not edit code, advance the checkpoint, or choose a later commit.
4. List `CHECKPOINT..UPSTREAM_DEVELOP` with `git rev-list --first-parent --reverse`. Select only the first SHA. Do not select by timestamp and do not skip ahead. If the list is empty, report that Super Kalium is current and stop.
5. Work from a clean branch based on the fetched `origin/develop`. Use `mo/upstream-<short-sha>` unless the user requests another branch name. Keep unrelated working-tree changes out of the migration. Use a separate clean worktree when needed.

Follow the repository's GitHub authentication rules. Run the requested `gh` command first, retry network failures with escalation, and request login only after an unrestricted HTTP 401 or `Bad credentials` response.

## Understand the upstream change

Read the full upstream commit message, author identity, parent, patch, and changed files. When the subject links an upstream PR, read that PR for intent and review context.

Extract every `WPB-[0-9]+` key from the commit message and upstream PR. If a Jira read integration is available, read each issue before editing. Use the issue to understand the problem, acceptance criteria, and edge cases. Jira access is optional. A missing connection or inaccessible ticket does not justify guessing, and it does not permit Jira writes. Do not copy private Jira details into a public PR.

Search the local code for the affected behavior, not only matching filenames. Read local history after the checkpoint when it explains why Super Kalium differs.

Read [references/adr-routing.md](references/adr-routing.md) to find likely ADRs. Read each candidate's status, context, decision, and consequences. An accepted Super Kalium decision may change how the upstream behavior fits locally, but it does not permit silently dropping the behavior.

## Preserve database migration order

This rule is non-negotiable. An upstream SQLDelight migration owns its numbered place in the migration chain. Super Kalium must remain upgrade-compatible with databases created by upstream Kalium.

Apply the rule separately to `db_user` and `db_global`:

- Keep every shared upstream migration at the same numbered filename and in the same relative order as upstream.
- When an incoming upstream migration uses a number already occupied by a Super Kalium-only migration, put the upstream migration at that number. Renumber the local migration and any later local-only migrations so they follow the imported upstream chain while preserving their local order.
- For example, if upstream adds `3.sqm` while Super Kalium already has its own `3.sqm`, import upstream `3.sqm` as `3.sqm` and rename the local migration to `4.sqm`.
- Do not merge upstream and local SQL into the same numbered file. If the upstream migration makes a local migration partly or fully redundant, remove the duplicate operation and keep any remaining Super Kalium-only change in a later migration.
- Renumber every test, fixture, schema snapshot, constant, class name, comment, and document that refers to a moved migration.
- The final schema must contain both the upstream change and every still-needed Super Kalium change.

Super Kalium is still in development, so it is acceptable to renumber its local-only migrations. Compatibility with upstream database versions takes priority over compatibility with unpublished Super Kalium migration numbers.

Before finishing a migration commit, compare the ordered migration lists with upstream. All shared migrations must have upstream's numbers and order. Super Kalium-only migrations must come after the latest imported upstream migration for that database. Verify that a database at the upstream schema version can run the remaining Super Kalium migrations without skipping a local change or applying one twice.

Run the migration checks when this rule applies:

```bash
./gradlew :data:persistence:jvmTest --tests com.wire.kalium.persistence.migrations.VerifyDatabaseMigrationsTest
```

## Classify the adaptation

Classify the upstream commit as one of these outcomes:

- Adapt the behavior to the current local design.
- Record that equivalent behavior already exists, with code and test evidence.
- Record that the change does not apply because Super Kalium removed or replaced the affected behavior, with evidence from the repository and ADRs.

Do not skip the SHA in any outcome. For the last two outcomes, the PR may change only the checkpoint, but its description must explain the evidence.

## Adapt and verify

Preserve the upstream feature or fix unless a local ADR or later local behavior requires a different implementation. Prefer the smallest change that fits the existing code. Do not copy obsolete upstream architecture into the fork and do not cherry-pick blindly.

Update or add tests for the behavior when the upstream patch has tests or the local change needs regression coverage. Run verification that covers the affected modules and behavior. Fix failures caused by the migration. Keep unrelated failures separate and state them plainly.

After the code and tests are ready, replace the checkpoint with the selected full upstream SHA. The checkpoint change belongs in the same commit as the adaptation.

Review the complete diff against `origin/develop`. Remove unrelated files, generated noise, and accidental changes. Confirm that the diff represents only the selected upstream commit and its checkpoint update.

## Preserve attribution

Create one semantic commit. Keep the upstream intent and `WPB` key in the subject when one exists. Remove the upstream PR number suffix because that number belongs to `wireapp/kalium`.

Use a commit body in this shape:

```text
Adapted from wireapp/kalium@<full-upstream-sha>.

Upstream-Commit: <full-upstream-sha>
Co-authored-by: <upstream-author-name> <upstream-author-email>
```

Always add the upstream commit's primary author as a `Co-authored-by` trailer, including bot authors. Preserve any valid `Co-authored-by` trailers from the upstream commit and remove duplicates. Keep all co-author trailers at the end of the message.

Before pushing, require `git rev-list --count origin/develop..HEAD` to equal `1` and require `origin/develop` to be an ancestor of `HEAD`. A count of one is not enough when both refs have advanced. If either check fails, rebuild, rebase, or squash the branch without rewriting unrelated user history.

## Open one PR and stop

Fetch `origin/develop` again immediately before pushing. Confirm that its checkpoint still equals the previous checkpoint. Query the open-PR lock again. If the checkpoint moved or another migration PR appeared, stop without pushing or opening a PR.

If `origin/develop` advanced but its checkpoint did not, rebase the single migration commit onto the refreshed base and rerun affected verification. Then repeat the final fetch, checkpoint, ancestry, commit-count, and lock checks. Push only when `git rev-list --left-right --count origin/develop...HEAD` reports zero base-only commits and one migration commit.

Push the branch and open one non-draft PR against `develop`. Use the commit subject as the PR title and follow `.github/pull_request_template.md`. The description must include:

- The upstream behavior and how Super Kalium adapts it.
- The original commit and upstream PR links.
- Each Jira key, without private issue details.
- The ADRs that affected the adaptation, or `None` when no ADR applies.
- The verification result, without listing the commands used.
- These exact machine-readable lines outside a code block:

```text
Upstream-Commit: <full-upstream-sha>
Previous-Upstream-Commit: <full-previous-checkpoint-sha>
```

After opening the PR, report its URL, the upstream SHA, the adaptation outcome, the Jira keys, the ADRs used, and the verification result. Do not merge the PR. Do not start the next upstream commit in the same invocation, even if the user asks to process several commits.

If product behavior is ambiguous or an accepted ADR conflicts with the upstream intent, stop before opening the PR and ask for the missing product decision. Leave the checkpoint unchanged.
