### Added
- Added `UserSessionScope.observeIsMeetingsEnabled` with `ObserveIsMeetingsEnabledUseCase` to observe whether meetings are enabled for the current user and supported by the current API version.

### Migration
No action required. Consumers can use `UserSessionScope.observeIsMeetingsEnabled()` when they need to react to meetings feature flag changes.

### Compatibility
ABI: additive.
Source: additive.
Behavior: no behavior change unless consumers call the new meetings feature flag observer API.
