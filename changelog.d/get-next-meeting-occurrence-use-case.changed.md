Renamed `MeetingScope.getNextMeetingOccurrence` and `GetNextMeetingOccurrenceUseCase` to `MeetingScope.getNextUnfinishedMeetingOccurrence` and `GetNextUnfinishedMeetingOccurrenceUseCase`. The API now returns the current ongoing occurrence when it has not finished yet, otherwise the next future occurrence.

  - ABI: breaking for consumers compiled against the previous next meeting occurrence API.
  - Source: breaking for consumers calling `MeetingScope.getNextMeetingOccurrence` or referencing `GetNextMeetingOccurrenceUseCase`.
  - Behavior: ongoing occurrences are now returned while their end time is after the supplied `from` instant.
  - Migration: replace `getNextMeetingOccurrence` with `getNextUnfinishedMeetingOccurrence` and handle that the returned occurrence may already be ongoing.
