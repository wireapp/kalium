### Added
- Added `CoreLogicCommon.registerLogger` so iOS consumers can configure or update Kalium logging after initialization.

### Migration
No action required. Call `registerLogger` only when runtime logger configuration is needed.

### Compatibility
ABI: additive.
Source: additive.
Behavior: logging configuration changes only when the new API is called.
