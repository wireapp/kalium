Meeting creation, update, and deletion use cases now expose specific outcomes for incomplete setup, participant changes, and deletion cleanup.

  - ABI: changed; create/update success results and delete failure results are now sealed hierarchies, and generic create/update failures retain their `CoreFailure`.
  - Source: changed for consumers constructing or matching the previous result types.
  - Behavior: create/update return `Success.Partial` with a `MeetingSaveWarning` when the meeting was saved but a subsequent step failed. Updates persist the meeting before applying participant changes. Deletion distinguishes cleanup failures after remote success from unsuccessful deletion and permission errors.
  - Migration: handle `Success.Complete` and `Success.Partial` for create/update; creation successes retain `meetingId` and `conversationId`. Read `Partial.warning`, including participant failure details as `Map<UserId, CoreFailure>`. Construct creation failures with `Failure(coreFailure)` and update failures with `Failure.Other(coreFailure)`. For deletion, handle `Failure.Other`, `Failure.CleanupFailed`, and `Failure.NotAllowed` where available.
