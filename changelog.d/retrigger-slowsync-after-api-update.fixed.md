Fixed slow sync being skipped when the negotiated backend API version changes. Exposed `GlobalKaliumScope.updateApiVersions` and `UpdateApiVersionsUseCase` to refresh API versions for locally stored sessions and report whether a persisted common API version changed.

  - ABI: additive public use case, scope property, and `Result` enum.
  - Source: additive.
  - Behavior: slow sync runs when the current scope's API version differs from the last successfully synced API version, including when no previous API version is recorded. The version is recorded only after slow sync succeeds. API version refresh returns `Changed` when a session's persisted common API version changes, `Unchanged` when no such change is detected, or `Failure` when sessions cannot be read before or after the refresh. Changes to other server metadata do not produce `Changed`.
  - Migration: existing sessions without a recorded slow sync API version perform an additional slow sync. Call `updateApiVersions()` and handle `Result.Changed`, `Result.Unchanged`, and `Result.Failure` to react to API version refreshes.
