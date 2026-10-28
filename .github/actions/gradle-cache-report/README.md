# Gradle cache reports

The iOS shards append cache and task-reuse diagnostics to their GitHub job summaries.
The existing `ios-build-timings-<shard>` artifact includes `build/ci/cache-report.json`
and the original Gradle HTML profiles. No external reporting service is used.

Cache lookup results distinguish an exact key hit, a fallback restore, a cold start,
and a skipped or failed restore. A new commit normally misses the exact output-cache
key, so `cache-hit: false` alone does not mean a cold cache. The matched key is recorded.
Restore/save durations cover the corresponding step groups, including action overhead.
Reported sizes are logical file contents at job end, excluding symlinks; they are not
compressed transfer sizes. The cache actions' logs contain upload/download details.

Task outcomes and the slowest executed tasks come from the existing `--profile`
reports. Counts include non-cacheable tasks and separate invocations; they do not
estimate time saved or a hit percentage among eligible tasks. Missing or incomplete
profiles are reported explicitly. Gradle profiles leave the result blank for both
successful and failed executions; the report labels both as executed and displays
workflow build/test outcomes separately. Reporting is best effort and does not
change the build or test result.

Develop remains the writer for shared dependencies and `.konan`. Successful binary
builds on trusted same-repository PRs can additionally save their own shard outputs.
GitHub scopes these caches to the PR merge ref, allowing follow-up commits on that
PR to reuse them. Forks and unrelated events remain excluded by the shared policy;
merge-queue runs stay read-only. Shards with no selected tests do not save another
snapshot. Review cache sizes and eviction pressure before expanding PR cache writes.

Run the reporting regressions with:

```sh
python3 -m unittest discover -s .github/actions/gradle-cache-report -p 'test_*.py'
```
