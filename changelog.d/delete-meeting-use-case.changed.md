Changed `MeetingScope.deleteMeeting` with `DeleteMeetingUseCase` to `MeetingScope.deleteMeetingForEveryone` with `DeleteMeetingForEveryoneUseCase`.

  - ABI: changed
  - Source: changed for consumers using the meeting deletion API.
  - Behavior: unchanged; the use case still deletes the meeting for every participant.
  - Migration: use `MeetingScope.deleteMeetingForEveryone` and `DeleteMeetingForEveryoneUseCase`.
