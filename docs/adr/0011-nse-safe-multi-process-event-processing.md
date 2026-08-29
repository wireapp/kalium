# 11. NSE-safe multi-process event processing

Date: 2026-08-18

## Status

Proposed

## Context

Wire's iOS application and Notification Service Extension (NSE) can execute concurrently for the
same account. Both processes may receive the same event, open the same Kalium and CoreCrypto stores,
and attempt to mutate the same protocol and application state.

The existing incoming-event path was designed around one process. Its event cursor, Kotlin mutexes,
SQLDelight listeners, notification flows, and object scopes are process-local. Push-channel
ownership may reduce duplicate work, but it is not a correctness boundary. Starting the main
application's full synchronization graph in an extension would also introduce long-lived observers,
workers, and network activity into a deadline-constrained process.

CoreCrypto is already used by the current iOS application and is assumed to serialize access to the
same crypto database safely across processes. Auditing or replacing that behavior is outside this
decision. If the assumption is disproved, CoreCrypto becomes a blocking dependency rather than a
second transaction implementation in Kalium.

Kalium and CoreCrypto do not share one atomic transaction. Incoming-event processing must therefore
tolerate a crash between crypto mutation, application persistence, durable side effects, and the
processed-event marker.

## Decision

Kalium will provide one shared incoming-event implementation for the main application and NSE, with
different lifecycle facades around it.

The shared event-processing core belongs below `:logic` in `:domain:event-processing`. It owns the
event and delivery contracts, event source, batch coordination, dispatch rules, receiver contracts,
and processing logging. Concrete receivers and handlers used by the NSE must also live below
`:logic`, and the main application must use those same implementations. NSE-specific copies of the
receive pipeline are not allowed.

The main application continues to own continuous synchronization, including event gathering,
WebSocket lifetime, catch-up, sync-state publication, and long-lived observers. The NSE uses a
bounded one-shot facade exported from a dedicated `:nse` module and Apple framework named
`KaliumNSE`. `:nse` must not depend on `:logic`, link `KaliumLogic`, construct `CoreLogic` or
`UserSessionScope`, or start continuous synchronization.

Incoming-event correctness follows these rules:

- Acquire an account-scoped cross-process event lock before querying authoritative unprocessed
  events.
- Never treat a batch selected by a process-local flow before lock acquisition as authoritative.
- Preserve the global lock order: account event lock, Proteus, MLS, then the Kalium user-database
  transaction. Release in reverse order where possible.
- Process events in persistent order, initially with one event per crypto transaction.
- Treat delivery as at-least-once. Crypto updates, application persistence, and durable effects must
  be idempotent under retry.
- Mark an event processed only after its required crypto and application mutations have committed.
- Do not perform network or unbounded work while the event or crypto locks are held. Persist required
  follow-up work as a durable side effect.
- Bound NSE work with a monotonic deadline, event limit, and cancellation-safe event boundaries.

The main application and NSE use the same App Group account roots and keychain access group. NSE
initialization opens existing state only: it does not create accounts, identities, secrets, stores,
or run migrations and repairs. Missing or incompatible state produces a typed result requiring the
main application.

Notification intent and cross-process follow-up work are stored in an idempotent durable outbox.
Process-local `SharedFlow`s may remain as adapters but are not authoritative. Writes made by the NSE
publish durable invalidation generations and a Darwin notification so a running or relaunched main
application can refresh its local SQLDelight observers without relying on notification delivery for
correctness.

`KaliumNSE` exposes concrete Swift-friendly input, budget, result, notification, and acknowledgement
types. It does not export generic `Either` values. Results distinguish completion, no work,
contention, deadline exhaustion, main-app-required state, and failure.

Wire iOS uses this as its single NSE event-processing path. There is no legacy NSE processing path
to preserve and no runtime rollout switch.

### Current extraction boundary

MLS receive unpacking uses the shared architecture boundary without changing main-app runtime
behavior. `KaliumSyncException` is owned by `:domain:event-processing`; the
`PendingProposalScheduler` contract and `MLSMessageUnpacker` implementation are owned by
`:domain:messaging:receiving`; and the shared `MLSMessageFailureHandler` classification model is
owned by `:domain:messaging:shared`. The unpacker consumes only focused protocol, subconversation,
decryptor, scheduler, and protobuf-decoder contracts.

Main-app composition supplies the same `conversationRepository` as protocol getter, stable
process-local `subconversationRepository` as group-info provider, observable
`mlsConversationRepository` wrapper as decryptor, eager `PendingProposalSchedulerImpl`, and
`protoContentMapper` as protobuf decoder. The observable decryptor therefore retains its
success-only crypto-state hook, while failed decrypts do not notify it; decrypt, scheduling,
decoding, classification, logging, exception, and cancellation behavior remain unchanged.

Concrete new-message orchestration, Proteus unpacking, and application-message routing are also
owned by `:domain:messaging:receiving`. The new-message handler retains focused dependencies on the
two unpackers, application-message handler, and self user ID. Legal-hold handling, stale-epoch
verification, and MLS reset cross the boundary as focused suspend functions; main-app composition
supplies the existing `LegalHoldHandler`, `StaleEpochVerifier`, and a once-per-handler captured
`ResetMLSConversationUseCase`. Self-deletion and confirmation-delivery likewise remain injected
actions. Callback results that were ignored remain ignored, and ordering, logging, persistence,
failure classification, exception, and cancellation behavior remain unchanged.

The small member-change and MLS-reset conversation-state handlers are also owned by
`:domain:messaging:receiving`. Member change retains the focused lifecycle repository, message
persistence, and self user ID directly; fetch-if-unknown crosses as only
`suspend (CryptoTransactionContext, ConversationId) -> Either<CoreFailure, Unit>`. Main-app
composition captures one exact existing fetch use-case instance per handler construction and keeps
its default `ConversationSyncReason.Other` call. MLS reset consumes only `MLSResetEventRepository`;
the broad logic-owned `MLSConversationRepository` extends that contract, and composition supplies
the same observable repository wrapper used before so successful leave operations retain the
crypto-state hook and existing delegate behavior. Role-read/fetch/update/persist and
leave/check/conditional-epoch/update ordering, ignored returned failures, exact promotion and group
state data, logs, exception propagation, and cancellation propagation remain unchanged.

Protocol-update event handling is likewise owned by `:domain:messaging:receiving` under its existing
package. It retains `SystemMessageInserter` directly and accepts only focused protocol-update and
established-call suspend functions from main-app composition. `UserSessionScope` captures one exact
update use-case instance, call-repository object, and system-message-inserter instance per handler
construction. The update action retains `localOnly = true`; the call action retains
`establishedCallsFlow().first().isNotEmpty()`. Update, protocol-message insertion, call observation,
and optional during-call-message order; the non-`MIXED` call observation; deleted-conversation
classification; logging; returned failures; exceptions; and cancellation remain unchanged.

MLS-welcome event handling is likewise owned by `:domain:messaging:receiving` under its existing
package. It consumes a focused `MLSWelcomeEventRepository`, which extends the shared protocol getter
and exposes only group-state update and conversation-details observation, plus focused callbacks for
one-to-one resolution, key-package refill, CRL checking/persistence, external-commit rejoin, and
fetch-if-unknown. The logic-owned `ConversationRepository` extends the focused contract, and
main-app composition passes the same repository object while capturing the remaining exact objects
once in their original evaluation order. MLS-null handling, fetch/process/CRL/establish/resolve
order, first-flow observation, orphan recovery, outcome logging, returned failures, exceptions, and
cancellation remain unchanged.

New-conversation and deleted-conversation event handling are likewise owned by
`:domain:messaging:receiving` under their existing packages. They share a focused user boundary;
new-conversation consumes only the five required system-message operations plus captured self-team,
persistence, one-to-one resolution, and existing type-mapper actions, while deletion consumes a
focused lookup plus the direct notification manager/hook and one captured deletion action. The
broad logic repositories and message creator extend the focused contracts without adapters or
duplicate state. New-conversation crosses persistence through a focused two-argument event-semantic
callback; main-app composition keeps `ConversationSyncReason` logic-owned and invokes the captured
persistence use case with `ConversationSyncReason.Event`. Composition captures the same dependency
objects once and in original argument order. Persistence/resolution/update/fetch/message order,
independently ignored message failures, lookup/delete/user-flow/notification behavior, the normally
unconditional deletion hook after returned failures, logging, exceptions, and cancellation remain
unchanged.

Member-join and member-leave event handling are likewise owned by
`:domain:messaging:receiving` under their existing packages and ordinary interface contracts. They
reuse the receiving-owned lifecycle and message persistence boundaries, focused system-message,
protocol, and MLS contracts, plus minimal member-event conversation/user contracts implemented
directly by the broad logic repositories. Main-app-only conversation fetch, legal-hold refresh,
call-client update, and self-team lookup cross captured callbacks. Composition retains the original
constructor evaluation order, fetch default semantics, and delayed forcing of the existing
call-update `Lazy`; join/leave ordering, result ownership, ignored results, transaction-context
behavior, messages, logging, exceptions, and cancellation are unchanged.

`ConversationEventReceiverImpl` and its focused tests are also owned by
`:domain:messaging:receiving`, while the `ConversationEventReceiver` contract remains owned by
`:domain:event-processing`. The implementation retains its package, FQCN, normal class shape,
constructor dependencies, branch order, result ownership, forwarding identity, flush behavior,
exception behavior, and cancellation behavior. Main-app composition constructs the same FQCN with
the same dependencies. `UserEventReceiverImpl` remains logic-owned and outside the conversation
receiver ownership boundary.

This boundary is not an NSE runtime implementation. The broad legal-hold, stale-epoch, reset/rejoin,
confirmation-delivery, self-deletion, and pending-side-effect implementations remain app-owned and
need explicit NSE ownership/adapters or a durable action/outbox design. Subconversation group
mapping remains process-local and requires durable `(conversationId, subconversationId) -> groupId`
state before a second process can use it. Pending-proposal work still needs explicit ownership, a
durable outbox, and a main-app executor; the extension must not construct the current scheduler
implementation or a no-op substitute. The Kalium account event lock, validation of the assumed
CoreCrypto process serialization, NSE facade wiring, and the broad protobuf encoder/mapper plus
`AssetMapper` graph remain separate work; no separate Kalium CoreCrypto database-lock implementation
is planned. Durable/asynchronous deletion, main-app side-effect execution, shared-storage bootstrap,
and rollout remain future work.

### Rejected alternatives

- **Start full synchronization in the NSE.** Rejected because it creates long-lived and potentially
  unbounded work inside an extension with a strict execution deadline.
- **Link `KaliumLogic` and construct a reduced `UserSessionScope`.** Rejected because the module and
  scope expose the extension to unrelated observers, workers, calls, analytics, and future
  initialization side effects.
- **Create a separate NSE receive pipeline.** Rejected because the implementations would diverge and
  make idempotency and crypto correctness dependent on entry point.
- **Rely on push ownership, Kotlin mutexes, Swift actors, or SQLDelight listeners.** Rejected because
  they do not serialize work across processes.
- **Query events before acquiring ownership.** Rejected because another process can process or
  mutate that batch before the current process owns it.
- **Provide exactly-once processing across CoreCrypto and Kalium SQLDelight.** Rejected because the
  stores do not participate in one atomic transaction; retryable at-least-once processing is the
  recoverable model.

## Consequences

**Easier:**

- The app and NSE share one processing implementation and correctness test matrix.
- The NSE binary avoids the full `:logic` dependency and application lifecycle graph.
- Concurrent app/NSE execution, duplicate delivery, crashes, and lock contention have explicit
  recovery behavior.
- Typed bounded results let Wire iOS complete notification requests safely.
- Notification intent and main-app refresh survive process termination.

**More difficult:**

- Event-processing contracts and the required receiver graph must remain below `:logic`.
- Cross-process lock ordering and deadline behavior require process-based Apple tests; coroutine-only
  tests are insufficient.
- Receiver work that currently performs network access must be split into durable mutation and
  deferred execution.
- At-least-once processing requires explicit idempotency at every crypto, persistence, and outbox
  boundary.
- Shared App Group storage, keychain access, file protection, migrations, and entitlements require
  coordinated Kalium and Wire iOS changes.

## References

- [NSE-safe multi-process event processing implementation plan](../plans/nse-safe-multi-process-event-processing.md)
- [ADR 4: Module Boundary Restructuring](0004-module-boundary-restructuring.md)
- [ADR 7: Swift-Friendly Result Types for Public APIs](0007-swift-friendly-result-types.md)
