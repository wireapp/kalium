`UserSessionScope.syncMeetingsUseCase` and `SyncMeetingsUseCase` made public so consumers can trigger meeting synchronization and check whether meetings are enabled.

  - ABI: additive.
  - Source: additive.
  - Behavior: synchronization removes locally stored meetings when the backend returns an empty list.
  - Migration: no action required; consumers can call `UserSessionScope.syncMeetingsUseCase()` to trigger synchronization.
