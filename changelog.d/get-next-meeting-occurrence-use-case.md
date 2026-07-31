### Added
- Added `MeetingScope.getNextMeetingOccurrence` with `GetNextMeetingOccurrenceUseCase` to get the next occurrence for a given meeting that has not started yet.

### Migration
No action required unless consumers want to expose the next upcoming occurrence for a specific meeting.

### Compatibility
ABI: additive.
Source: additive.
Behavior: no behavior change unless consumers call the new next meeting occurrence API.
