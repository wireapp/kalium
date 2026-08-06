Added `MeetingScope.updateMeeting` with `UpdateMeetingUseCase` to update an existing meeting.

  - ABI: additive
  - Source: additive
  - Behavior: no behavior change unless consumers call the new meeting update API.
  - Migration: no action required unless consumers want to expose meeting updates.
