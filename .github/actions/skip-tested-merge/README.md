# Develop cache builds after merge-queue tests

Call this action after checkout with `actions: read`. Only develop pushes query the
API. `skip-tests=true` requires a completed, successful `merge_group` run of the
same workflow file at the exact push SHA, on the first run attempt. Missing runs,
API failures and manual re-runs retain the full test run. Push runs use their own
concurrency group (`push-<sha>`), so they never cancel the merge-queue run they
look for. PR and merge-queue grouping and test selection stay unchanged.

A skipped test run still sets up Gradle and builds the following tasks so develop
continues to supply caches to read-only PR and merge-queue runs:

| Workflow | Cache build | Reason |
| --- | --- | --- |
| JVM/JS | `jvmTestClasses jsTestClasses`, existing sample compilation | Compile production and test code without executing tests or generating coverage. |
| Android unit | `compileAndroidHostTestSources`, existing sample compilation | Compile production and host-test sources across modules. |
| Database | `:data:persistence:jvmTestClasses` | Compile the migration test suite and its dependencies without running migrations. |
| Android instrumented | Existing `packageAndroidDeviceTest` | Build/dex/package the test APKs without starting an emulator. |
| iOS | Existing per-shard `linkDebugTestIosSimulatorArm64` selection | Compile and link all test binaries without booting a simulator; retain existing dependency, shard-output, and Konan cache saves. |

These builds populate compiled task outputs and dependencies restored by PR jobs.
They do not refresh test-execution or coverage outputs; those are produced when
PRs run their tests. Gradle setup's post step saves the standard caches; iOS keeps
its explicit cache saves. Cache-only builds still fail the workflow if they fail.

Run the lookup checks with:

```sh
node --test .github/actions/skip-tested-merge/test_check.cjs
```

For a live read-only API check, export `GH_TOKEN`, `GITHUB_EVENT_NAME`,
`GITHUB_REF`, `GITHUB_REPOSITORY`, `GITHUB_SHA`, and `GITHUB_WORKFLOW_REF`, then run
`node .github/actions/skip-tested-merge/check.cjs`.
