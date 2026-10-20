Added `UserSessionScope.slowSyncStatus` and optional processed/total conversation counts on `SlowSyncStatus.Ongoing` for observing initial synchronization progress.

- Source: existing Kotlin calls to `Ongoing(currentStep)` remain valid; the new progress argument defaults to `null`.
- ABI: the public `Ongoing` data-class constructor and generated `copy` signatures change. Recompile consumers against this version.
- Behavior: counts describe processed MLS conversations in the current attempt, including recovery deferred by the existing join policy. Other stages have no measured fraction. Retry and joining policies are unchanged.
