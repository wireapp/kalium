# Sequential migration and fidelity review

Use this workflow for a single migration, a batch, or review of an existing migration stack. The sequence is:

`migrate and verify -> Astra xhigh review -> implementation categorization and fixes -> re-review changed head -> open PR -> next commit on that head`

Create the stack without merging unless the user requests merges. If the user requests a merge before each next migration, complete that merge and use refreshed `develop` for the successor.

## Coordinator and workers

Keep one coordinator responsible for upstream order, base selection, handoffs, PR publication, and any authorized merges. Only one queue entry is active at a time. Complete its implementation, review loop, and PR before starting the next entry. Do not parallelize dependent migrations or reviews.

Use a separate implementation worker and separate review worker for every upstream commit. When the user explicitly requests chats, create one implementation chat and one review chat per commit with the app's chat tools. Otherwise use available delegated agents for these roles. Do not create user-owned chats without a request for them. Reuse the same implementation and review workers for that entry's corrections and later rebases.

Set each reviewer to `gpt-6-astra` with reasoning effort `xhigh`. Use the implementation worker's configured model unless the user specifies another. If the required reviewer configuration is unavailable, report that limitation before treating the review as complete; do not silently substitute a model or effort.

Pass the implementation worker its exact upstream SHA and PR, previous checkpoint, selected base SHA and branch, worktree, applicable repository instructions, and queue boundary. Require one migration commit, upstream credit, verification results, material adaptations, and exact final head. The worker must not start its successor or merge independently.

Record enough state to resume without rediscovering or duplicating work:

- Fetched upstream tip, initial downstream checkpoint, and ordered requested SHAs.
- For each entry, upstream SHA, previous checkpoint, base branch and exact SHA, downstream branch, worktree, and implementation/review worker IDs.
- Current head, exact head/base cleared by Astra, findings and implementation classifications, verification evidence and limits, PR URL/target, and merge SHA when applicable.

Keep this orchestration state out of the code commit. Do not infer a completed review from an idle worker or a matching branch name. Wait for its result and inspect the exact revision it reviewed.

## Astra fidelity review

Start review after implementation, required lint, and affected verification are ready. A new PR is not required for review: use the pinned local commit, selected base, upstream commit and source PR. Attach the migration PR to the review chat once it exists.

Give the reviewer the complete upstream source contract and exact downstream head/base. Ask it to read the full upstream diff and relevant source PR discussion, then trace complete local helpers, lifecycle, state, data paths, public API contracts, transaction behavior, and platform mappings. Read accepted fork ADRs that explain an adaptation. Compare upstream test setup, historical fixtures, action order, assertions, and failure boundaries with their local equivalents.

For SQLDelight changes, apply the database migration rules in `SKILL.md` separately to `db_user` and `db_global`. Compare shared migration numbers and order, local-only renumbering and references, final schema, and upstream-version upgrade coverage. A collision handled by combining SQL or leaving local-only migrations before the imported chain is a porting defect. Do not treat required database compatibility as an inherited upstream bug.

The reviewer is read-only. It must not fix code, change branches, build or install apps, clear sessions, push, merge, or post public GitHub reviews/comments. It may inspect existing verification artifacts. Its job is to identify evidenced fidelity defects and suggest categories; the implementation worker owns final categorization and corrections.

Use these categories:

| Category | Meaning and action |
| --- | --- |
| `can ignore` | A bug or limitation inherited from upstream, unrelated pre-existing fork behavior, or accurately disclosed runtime uncertainty with no demonstrated porting defect. Preserve upstream behavior and make no general bug fix. |
| `false positive` | The alleged deviation is disproved by actual source, helper or platform behavior, or an accepted fork decision. Record the evidence; make no correction. |
| `must fix` | An omitted or changed upstream behavior/test, incorrect required local mapping, adaptation-introduced regression, SQLDelight numbering or upgrade-compatibility violation, accepted ADR violation, checkpoint/credit/scope error, or inaccurate verification claim. Correct only that migration defect. |

An unrun live scenario alone does not prove a defect. A passing test alone does not prove the behavior it claims to cover. Check whether fixture state or selectors mask an assertion, and whether the test can detect the regression. Correct a demonstrated verification defect without expanding product behavior.

Do not request hardening, refactoring, or fixes for bugs in the canonical upstream implementation. An adaptation-introduced bug belongs to `must fix` because it breaks faithful porting.

Require a report with:

- Exact reviewed head and base SHAs, upstream SHA, and source PR.
- Each actual finding's ID, severity, file/line, upstream and local evidence, suggested category, and narrowest necessary correction.
- Evidence for significant fork mappings, checkpoint, attribution, commit scope, verification claims, and disclosed limits.
- An explicit no-findings result when appropriate. Do not invent issues to fill categories.

## Return findings to implementation

Relay the complete review report to that entry's implementation worker. Require it to classify every finding as `can ignore`, `false positive`, or `must fix`, with evidence. A disagreement with Astra needs source evidence, not an assertion that tests passed.

For `must fix`, make the smallest correction and amend the single migration commit. Preserve upstream behavior, credit, checkpoint, and unrelated code. Run required lint and affected verification for the correction. Repeat unaffected checks only when a new change or unresolved concern warrants it. Update verification claims and limits.

Send every changed head, including a rebase, to the same Astra reviewer. Supply the prior reviewed SHA, new exact head/base, correction delta, implementation classification, and fresh evidence. Astra must confirm resolved findings and identify any remaining must-fix issues at that exact revision. If disputed findings remain, return them to implementation with the evidence and continue the loop.

Finish this entry only when implementation categorization is complete and the exact current head has no unresolved must-fix finding. Leave an unchanged head alone when no correction is needed.

## Publish and extend the stack

Re-fetch the remote base and check the lock immediately before publication. Match the selected base's checkpoint to the entry's previous upstream SHA. Require the base as direct parent and exactly one commit with `0/1` divergence. Record the reviewed SHA and ensure the pushed and published head matches it.

If refs moved, rebuild the affected entry on the correct base and repeat affected verification and Astra re-review. Do not add another downstream commit to record a correction or checkpoint. Never rewrite an unrelated worker's branch.

Open the first PR against `develop`. Open each successor against its predecessor's branch, using the predecessor's final reviewed head as its implementation base. The PR diff must contain only that successor's adaptation and checkpoint update. Preserve the attribution and machine-readable markers required by `SKILL.md`.

After publication, attach the PR to the relevant chats, record it, and start the next queue entry in a new implementation/review pair. Do not stop after the first PR when the user requested the whole queue. Do not start a successor from an unfinished or unreviewed predecessor.

For an existing stack, map each PR to its original implementation worker and upstream SHA, then review entries sequentially. Relay findings to the original worker. Reuse that entry's review worker if later changes require another pass.

## Ordered merges when requested

Merge only within the user's requested scope. The coordinator performs merges after the review/categorization loop and required checks. Read current branch protection and required check results; do not hardcode an absence of required checks or bypass them. Report optional CI jobs still running and any verification limits.

Before each merge, fetch current `origin/develop` and the PR head. Require:

- The PR targets `develop`, with exactly one migration commit directly on its current base.
- Its current head/base are the revisions cleared by Astra, with no unresolved must-fix finding.
- Its base checkpoint equals the entry's previous upstream SHA, and its head checkpoint equals the selected upstream SHA.
- Required local verification and required GitHub checks are satisfied, or a limitation has been resolved as required by repository policy.

Use an exact-head merge guard, such as `gh pr merge --match-head-commit <reviewed-head>`. Choose a repository-supported strategy that preserves one downstream commit per upstream commit. For a squash merge, pass the semantic subject and original attributed commit body explicitly; preserve every valid upstream co-author trailer. Confirm GitHub reports the PR merged, fetch `develop`, and verify the resulting checkpoint, attribution, and commit order.

After that merge, prepare its immediate queued successor before reviewing or merging it:

1. Fetch `origin/develop` and the successor's remote branch. Keep the old selected base SHA from the sequence record, even if its branch was deleted or amended.
2. Rebase only the successor's own migration commit onto `origin/develop`, dropping the old stack boundary. For a single-commit entry, use `git rebase --onto origin/develop <old-base-sha> <successor-branch>`. A plain rebase may replay already merged predecessor commits.
3. Compare the migration delta with its prior revision. Resolve only required compatibility changes. Confirm the expected checkpoint transition, direct parent, one commit, `0/1` divergence, attribution, and clean scope.
4. Rerun affected verification and required lint on the new base. Re-review the rebased head in the same Astra chat and return its report to implementation for classification.
5. Push with an explicit lease against the freshly fetched remote head. Retarget the PR to `develop`, or confirm GitHub already did so. Update its base, dependencies, verification, and reviewed revision in the description.
6. Recheck the exact remote revision and merge it only after this entry's loop and gates pass. Then prepare the following successor.

Do not rebase later queued entries concurrently or replay their earlier copies of an amended predecessor. If a predecessor closes without merging or an external change breaks the checkpoint chain, repair that entry before continuing; do not skip it.

## Completion

Report all requested PRs and their stack or merge order, issue classifications, must-fix corrections, review configuration, verification results, and remaining runtime/CI limits. Verify the final branch checkpoint reaches the last queued upstream SHA, or the final `develop` checkpoint when merges were requested. Preserve unrelated user changes and sessions throughout.
