Added `MeetingScope.deleteMeetingForMe` with `DeleteMeetingForMeUseCase`.

  - ABI: additive
  - Source: additive
  - Behavior: resolves the meeting conversation by meeting ID, leaves it for the current user, and removes the meeting locally.
  - Migration: use `MeetingScope.deleteMeetingForMe` when consumers need a self-user-only deletion flow.
