Added `MeetingScope.observeMeeting` and `ObserveMeetingUseCase` to observe a stored meeting by ID, independently of its occurrences.

  - ABI: additive.
  - Source: additive.
  - Behavior: emits the current meeting, subsequent updates (including link changes), and `null` after deletion.
  - Migration: no action required. Use `MeetingScope.observeMeeting(meetingId)` to observe current meeting data.
