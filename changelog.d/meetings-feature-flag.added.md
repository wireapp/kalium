Added `UserSessionScope.isMeetingsEnabled` with `IsMeetingsEnabledUseCase` to let consumers check whether meetings are enabled for the current user and supported by the current API version.

  - ABI: additive
  - Source: additive
  - Behavior: meetings sync now respects the backend meetings feature flag in addition to API v16 support.
