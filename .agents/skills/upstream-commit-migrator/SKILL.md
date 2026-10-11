---
name: upstream-commit-migrator
description: "Migrate wireapp/kalium develop commits into Super Kalium in first-parent order, with Astra xhigh fidelity reviews and single-commit stacked PRs. Use for the next commit, a requested batch, or all pending commits. Preserve upstream author credit and required fork adaptations."
---

# Upstream commit migrator

Port upstream `develop` commits sequentially. For each commit, migrate and verify, obtain an adversarial GPT-6 Astra review at xhigh, return the findings to its implementation worker for categorization and must-fix corrections, then open a non-draft PR. Start the next commit only when the preceding PR is ready, using its reviewed head as the base.

Keep the implementation and tests as close as possible to the original upstream PR. Adapt only what Super Kalium's existing behavior, iOS/KMP support, accepted ADRs, or database compatibility requires. Inherited upstream bugs belong to `can ignore`; this workflow does not authorize general bug fixing.

Read [references/sequential-workflow.md](references/sequential-workflow.md) for orchestration, chat handoffs, review scope, stack bookkeeping, and ordered merges. A request for one commit ends after its PR. A batch or all-pending request continues through its full queue. Merge only when the user requests it.

## Repository contract

- Downstream repository: `https://github.com/wireapp/super-kalium.git`.
- Upstream repository: `https://github.com/wireapp/kalium.git`.
- Base branch: `develop` in both repositories.
- Checkpoint: `UPSTREAM_KALIUM_COMMIT.txt` at the repository root. It contains one full lowercase commit SHA and a trailing newline.
- Commit order: first-parent order on upstream `develop`, starting after the checkpoint.
- Delivery: one upstream commit becomes one downstream commit in one non-draft PR.
- Stack: the first migration bases on fetched `origin/develop`; each successor bases on the preceding reviewed migration head and targets its branch.
- Lock: allow open migration PRs recorded in the current authorized sequence. Stop for an untracked migration PR or another worker on the same upstream commit.

Each migration advances its branch's checkpoint to its upstream SHA. The selected base's checkpoint is the previous SHA, including when that base is an unmerged stack branch. Until the PR merges, `develop` keeps its old checkpoint. If a PR closes without merging, recover or replace that stack entry before starting its successor.

## Pass the gates before editing

1. Fetch `origin/develop` and upstream `develop`. Use an existing `upstream` remote when it points to `wireapp/kalium`. Otherwise fetch the URL into a local remote-tracking ref such as `refs/remotes/kalium/develop`. Do not change a configured remote that points somewhere else.
2. At the start of a sequence, read the checkpoint from fetched `origin/develop`, not a stale feature branch. Require exactly 40 lowercase hexadecimal characters and a trailing newline. Confirm that it lies on the fetched upstream first-parent chain. Stop if either check fails.
3. Query open PRs in `wireapp/super-kalium` with `gh pr list`. Inspect bodies and branches for `Upstream-Commit:` or `mo/upstream-`. Match known entries against the current sequence's recorded SHAs, checkpoint links, and workers. For any untracked migration, report its number, title, and URL, then stop before editing or choosing a later commit. Do not assume ownership from its author or branch prefix alone.
4. List `CHECKPOINT..UPSTREAM_DEVELOP` with `git rev-list --first-parent --reverse`. Record the fetched upstream tip and the requested queue, then process its SHAs in that order without skipping. For "all pending", finish this snapshot; do not keep extending it as upstream moves. Resume a known sequence from its last verified checkpoint. If the list is empty, report that Super Kalium is current and stop.
5. Work from a clean branch based on the selected base. Use fetched `origin/develop` for the first entry and the previous reviewed head for each stacked successor. Confirm that the base's checkpoint equals this entry's previous upstream SHA. Use `mo/upstream-<short-sha>` unless the user requests another name. Keep unrelated changes out of the migration and use a clean worktree when needed.

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

Use the upstream implementation and test diff as the starting point. Preserve its names, control flow, structure, and test cases wherever the local code permits. Make the smallest required compatibility changes. Do not expand the feature, refactor adjacent code, introduce new abstractions, or add speculative hardening during the port.

Keep test changes minimal:

- Adapt upstream tests and reuse existing fork tests, helpers, and historical fixtures first. Extend an existing case when a small assertion covers the change.
- Add tests beyond upstream only for a concrete regression risk introduced by the port that existing coverage does not exercise. Keep each case focused on observable behavior or a necessary failure boundary.
- Remove duplicate coverage and tests that merely verify calls to mocks or copied fields when a retained behavior test already exercises the same path. Do not recreate upgrade cases already covered by historical fixtures.
- Test the distinct platform or transaction behavior that changed. Do not repeat every protocol, platform, input variant, or interruption stage when existing tests already cover those mechanisms.
- Keep fixtures and setup short. Reuse established helpers; avoid adding test infrastructure or Arrangement classes for a few calls.

Run verification that covers the affected modules and behavior, including the existing regressions. Running more existing tests does not require adding more test code. Fix failures caused by the migration. Keep unrelated failures separate and state them plainly.

Run Super Kalium's repository lint before final review: `./gradlew detekt` for Kotlin changes. Choose affected JVM, Android, and iOS checks from the current repository CI and module instructions. For SQLDelight changes, include the migration checks above. Compilation and focused tests do not replace lint. Report checks or live scenarios that could not run, and preserve existing device sessions and data.

After the code and tests are ready, replace the checkpoint with the selected full upstream SHA. The checkpoint change belongs in the same commit as the adaptation.

Review the complete diff against both the selected base and the original upstream PR. Every change beyond upstream must address a required fork compatibility gap or an uncovered regression risk; remove it otherwise. Remove unrelated files, duplicate tests added during the port, generated noise, and accidental changes. Briefly explain material departures from upstream in the PR. Confirm that the diff represents only the selected upstream commit, necessary adaptations, and its checkpoint update.

## Preserve attribution

Create one semantic commit. Keep the upstream intent and `WPB` key in the subject when one exists. Remove the upstream PR number suffix because that number belongs to `wireapp/kalium`.

Use a commit body in this shape:

```text
Adapted from wireapp/kalium@<full-upstream-sha>.

Upstream-Commit: <full-upstream-sha>
Co-authored-by: <upstream-author-name> <upstream-author-email>
```

Always add the upstream commit's primary author as a `Co-authored-by` trailer, including bot authors. Preserve any valid `Co-authored-by` trailers from the upstream commit and remove duplicates. Keep all co-author trailers at the end of the message.

Before review and pushing, require the selected base to be the direct parent of `HEAD`, `git rev-list --count BASE_SHA..HEAD` to equal `1`, and `git rev-list --left-right --count BASE_SHA...HEAD` to report `0/1`. A stacked head contains predecessor commits relative to `develop`; measure its own migration against its selected base. If these checks fail, rebuild, rebase, or squash only that migration without rewriting unrelated user history.

## Review, open the PR, and continue

Follow the Astra review and implementation categorization loop in [references/sequential-workflow.md](references/sequential-workflow.md). Open the PR only after the exact head has no unresolved `must fix` issues. For a PR that already exists, apply the same loop before advancing the sequence or merging.

Immediately before pushing, fetch `origin/develop` and the selected remote base branch. Recheck its SHA, checkpoint, and the sequence's open-PR lock. If the base changed, rebuild or rebase only this entry onto the correct base, rerun affected verification, and send the new head to the same Astra reviewer. Do not push or open a PR for an unreviewed revision. If a predecessor merged, use the ordered-merge procedure in the workflow reference.

Push the branch and open one non-draft PR against the selected base branch. Use an explicit force-with-lease against the freshly fetched remote head for updates. Use the commit subject as the PR title and follow the repository's PR template when present. The description must include:

- The upstream behavior and how Super Kalium adapts it.
- The original commit and upstream PR links.
- Each Jira key, without private issue details.
- The ADRs that affected the adaptation, or `None` when no ADR applies.
- The verification result, without listing the commands used.
- The predecessor PR when stacked, reviewed head and base SHAs, and any unverified runtime behavior.
- These exact machine-readable lines outside a code block:

```text
Upstream-Commit: <full-upstream-sha>
Previous-Upstream-Commit: <full-previous-checkpoint-sha>
```

After opening the PR, attach it to the relevant chats and record its URL, upstream SHA, reviewed head and base, adaptation outcome, Jira keys, ADRs, verification, and issue categories. For a batch, start the next entry only after this PR is published and ready. Its implementation uses this reviewed head as its base. Continue until every requested queue entry has a reviewed PR or a concrete blocker prevents progress.

When the user requests merges, follow the workflow reference to merge in order, then rebase and retarget each successor to `develop`. Preserve one attributed downstream commit per upstream commit. Do not merge merely because the stack is ready.

If product behavior is ambiguous or an accepted ADR conflicts with upstream intent, ask for the missing product decision before advancing that entry or opening its PR. Leave its base checkpoint unchanged and do not skip to a successor.
