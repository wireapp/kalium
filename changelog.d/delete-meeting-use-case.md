### Added
- Added `MeetingScope.deleteMeeting` with `DeleteMeetingUseCase` to delete a meeting by ID.

### Migration
No action required unless consumers want to expose meeting deletion.

### Compatibility
ABI: additive.
Source: additive.
Behavior: no behavior change unless consumers call the new meeting deletion API.
