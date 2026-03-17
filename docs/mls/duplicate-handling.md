# MLS Duplicate Handling

This file documents how Android handles replay / duplicate processing for the three main MLS event types:

1. `Conversation.MLSWelcome`
2. `Conversation.NewMLSMessage`
3. `Conversation.MLSReset`

This is intentionally separate from sync/runtime documentation:
- [Incremental Sync and Failure Semantics](./incremental-sync.md) explains why replay can happen at the queue/runtime level.
- [Runtime Timeline and Concurrency](./runtime-concurrency.md) explains how replay can overlap with other MLS flows.

## Why duplicates can happen at all

Android does not have a formal exactly-once guarantee across:
- process death,
- app kill,
- sync restart,
- observer restart,
- pending/live merge in the local event queue.

The key reasons are:
- `lastEmittedEventId` is in-memory only,
- events are marked processed only after the batch completes,
- live events can be replaced by pending copies with the same event id,
- crash/cancel boundaries can replay an already-seen logical event.

Current mitigation:
- `IncrementalSyncWorker` finishes the current batch and `setEventsAsProcessed(...)` inside `NonCancellable`, reducing duplicate reprocessing during normal sync cancellation.

Sources:
- [`../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/IncrementalSyncWorker.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/IncrementalSyncWorker.kt)
- [`../../logic/src/commonMain/kotlin/com/wire/kalium/logic/data/event/EventRepository.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/data/event/EventRepository.kt)

## Duplicate `Conversation.MLSWelcome`

Primary handler:
- [`../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSWelcomeEventHandler.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSWelcomeEventHandler.kt)

### Possible duplicate outcomes

- `ConversationAlreadyExists`
- `OrphanWelcome`
- second establish attempt
- external-commit recovery if the duplicate is not recognized as harmless

### Current Android behavior

#### `ConversationAlreadyExists`

Android:
- wipes the local conversation in core-crypto,
- retries the same welcome once.

This is not a pure no-op path. It is a destructive local repair plus retry.

#### `OrphanWelcome`

Android distinguishes two sub-cases:

1. If local DB says `ESTABLISHED` and core-crypto confirms the local group exists:
   - treat it as an already-handled welcome,
   - skip external-commit rejoin.

2. Otherwise:
   - assume the welcome cannot be safely ignored,
   - discard it,
   - call `JoinExistingMLSConversationUseCase`.

### Why duplicate welcome is the highest-risk duplicate

If Android does not recognize the duplicate as harmless:
- it can recover by external commit,
- and messages sent between the original welcome timing and the rejoin point may be lost/decryption-failed for that client.

## Duplicate `Conversation.NewMLSMessage`

Primary handler:
- [`../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/NewMessageEventHandler.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/NewMessageEventHandler.kt)

Failure mapping:
- [`../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/MLSMessageFailureHandler.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/MLSMessageFailureHandler.kt)

### Possible duplicate outcomes

- duplicate decrypt attempt,
- `DuplicateMessage`,
- buffered/future/stale classifications,
- repeated downstream side effects if the crash boundary is bad.

### Current Android behavior

`NewMessageEventHandler` maps MLS failures into:
- `Ignore`
- `InformUser`
- `OutOfSync`
- `ResetConversation`

The safest duplicate case is when replay resolves to a no-op/ignore-style failure.

### Real duplicate risk is not just decryption

Successful `NewMLSMessage` processing also triggers side effects:
- persist application message content,
- insert decryption/system state,
- schedule delivery confirmations,
- schedule self deletion,
- legal hold handling.

So duplicate safety depends on both:
- crypto/message failure mapping,
- idempotency of downstream side effects.

### Practical Android position

Android is relatively safe if replay resolves to:
- `DuplicateMessage`
- buffered/future/no-op style failures

Android is less safe if replay happens after:
- partial success,
- side effects already happened,
- process crashes before the event is marked processed.

## Duplicate `Conversation.MLSReset`

Primary handler:
- [`../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSResetConversationEventHandler.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSResetConversationEventHandler.kt)

### Possible duplicate outcomes

- repeated leave/wipe of old group,
- repeated metadata update to new group id/state/epoch,
- second pass classifying the conversation as already-established vs pending-after-reset.

### Current Android behavior

This handler is more repair-oriented than welcome handling:
- update local metadata to the new group information,
- if new group already exists locally, mark it established,
- otherwise leave it pending-after-reset.

### Practical risk level

Duplicate `MLSReset` is usually less dangerous than duplicate welcome because:
- it already assumes local state may need repair,
- repeated application often converges toward the same metadata outcome.

It is still not guaranteed to be a strict no-op because:
- the second processing attempt may observe a different local crypto state than the first,
- especially if another recovery flow already ran in between.

## Summary Matrix

| Event type | Typical duplicate outcomes | Safest duplicate case | Highest-risk duplicate case |
|---|---|---|---|
| `MLSWelcome` | `ConversationAlreadyExists`, `OrphanWelcome`, second establish, external rejoin | `OrphanWelcome` recognized as already-handled welcome | false-negative duplicate causing external rejoin |
| `NewMLSMessage` | `DuplicateMessage`, buffered/stale classifications, replayed side effects | failure maps to `Ignore` and no side effects repeat | partial success before crash, replay re-triggers side effects |
| `MLSReset` | repeated metadata sync, second leave/wipe, state reclassification | second pass converges to same repaired state | second pass sees different local state and takes different branch |

## Review Questions

1. Which of these three event types are expected to be idempotent by design?
2. Which duplicate cases are intentionally “recover by reset/rejoin” rather than strict no-op?
3. For `NewMLSMessage`, which downstream side effects are guaranteed idempotent and which are only best-effort?
4. Can core-crypto provide a stronger signal to distinguish:
   - already-processed welcome
   - genuinely orphaned welcome
5. Should duplicate `MLSReset` be explicitly recognized/logged, or is the current repair-oriented behavior sufficient?
