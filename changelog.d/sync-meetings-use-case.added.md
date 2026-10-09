Added public `UserSessionScope.syncMeetingsUseCase` and `SyncMeetingsUseCase` so consumers can trigger meeting synchronization and check whether meetings are enabled. The use case returns `SyncMeetingsUseCase.Result.Success` or `Failure`, which includes the underlying `CoreFailure`.

  - ABI: additive.
  - Source: additive.
  - Behavior: synchronization removes locally stored meetings when the backend returns an empty list.
  - Migration: no action required; consumers can call `UserSessionScope.syncMeetingsUseCase()` to trigger synchronization.
