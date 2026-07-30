### Added
- Added `MeetingScope.createNewMeeting` with `CreateNewMeetingUseCase` to create a new meeting.

### Migration
No action required unless consumers want to expose meeting creation.

### Compatibility
ABI: additive.
Source: additive.
Behavior: no behavior change unless consumers call the new meeting creation API.
