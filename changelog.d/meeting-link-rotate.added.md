### Added
- Added `MeetingScope.rotateMeetingLink` / `RotateMeetingLinkUseCase` to revoke a meeting guest link and create a replacement with an optional password (API v19). The updated meeting returned by the server is persisted directly and emitted to observers.

### Migration
No action required.

### Compatibility
ABI: additive.
Source: additive.
Behavior: refresh revokes the previous link; omitting the password creates an unprotected link.
