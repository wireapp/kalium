Added `MeetingScope.observeHasOngoingMeeting` to observe whether any locally stored, list-eligible occurrence falls within caller-supplied inclusive timing bounds, independently of pagination.

  - ABI: additive
  - Source: additive
  - Behavior: no change unless consumers use the new observer. Callers retain ownership of the list cutoff and ongoing grace period and refresh the bounds as time changes.
  - Migration: no action required.
