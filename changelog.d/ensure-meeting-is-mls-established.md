### Added
- Added `MeetingScope.ensureMeetingIsMLSEstablished` with `EnsureMeetingIsMLSEstablishedUseCase` to let consumers ensure a meeting conversation's MLS group is established before joining.

### Migration
No action required unless consumers want to proactively establish MLS for meeting conversations.

### Compatibility
ABI: additive.
Source: additive.
Behavior: no behavior change unless consumers call the new meeting MLS establishment API.
