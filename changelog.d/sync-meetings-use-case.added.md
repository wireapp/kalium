Added public `UserSessionScope.syncMeetingsUseCase` and `SyncMeetingsUseCase` so consumers can trigger meeting synchronization and check whether meetings are enabled. The use case returns `SyncMeetingsUseCase.Result.Success` or `Failure`, which includes the underlying `CoreFailure`.

  - ABI: additive.
  - Source: additive.
  - Behavior: synchronization prepares meeting conversations before storing meetings and removes locally stored meetings when the backend returns an empty or wholly unsupported list. Only meeting-list fetch errors fail synchronization; conversation and meeting persistence failures remain best effort, as in slow sync.
  - Migration: no action required; consumers can call `UserSessionScope.syncMeetingsUseCase()` to trigger synchronization.
