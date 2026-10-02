Fixed slow sync being skipped when the negotiated backend API version changes. Added `UserSessionScope.observeApiVersionChange` and `ObserveApiVersionChangeUseCase` to observe whether the stored API version differs from the version captured by the user session scope.

  - ABI: additive.
  - Source: additive.
  - Behavior: slow sync runs when the current scope's API version differs from the last successfully synced API version, including when no previous API version is recorded. The version is recorded only after slow sync succeeds.
  - Migration: no source changes required. Existing sessions without a recorded slow sync API version perform an additional slow sync. Consumers can observe `observeApiVersionChange()` to detect when their user session scope uses an outdated API version.
