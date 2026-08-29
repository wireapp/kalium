# NSE-safe multi-process event processing implementation plan

Date: 2026-08-17

## Status

Proposed

This document is the delivery plan for
[ADR 11: NSE-safe multi-process event processing](../adr/0011-nse-safe-multi-process-event-processing.md).
It records sequencing, milestone scope, validation gates, and unresolved implementation details;
the architecture decision itself remains independent of this plan.

## Context

Wire's iOS main application and Notification Service Extension (NSE) can run at the same time for
the same account. Both processes may receive the same event, open the same Kalium databases, and
mutate the same Proteus or MLS state. Process-local Kotlin mutexes, SQLDelight listeners, Swift
actors, and push-channel ownership alone cannot provide the required cross-process correctness.

This plan is based on Kalium `62c8f919bc13bcfeaf44c453054d89003b05c5b4` on `develop`. No other
branches or repositories were inspected while preparing it.

This plan assumes that the CoreCrypto version used by the current iOS application already safely
serializes access to the same crypto database across processes. Reimplementing or auditing
CoreCrypto process safety is therefore outside this plan. Kalium will use the existing transaction
API and map its observable contention, cancellation, and failure behavior into the NSE result.

### Current Kalium event path

```text
Notification API / WebSocket
    -> EventDataSource inserts Events rows with INSERT OR IGNORE
    -> EventGatherer observes unprocessed rows through a Flow
    -> EventRepository keeps a process-local lastEmittedEventId cursor
    -> IncrementalSyncWorker receives an already selected batch
    -> broad NonCancellable scope
    -> combined CoreCrypto transaction (Proteus, then MLS)
    -> EventProcessor and domain receivers
    -> independent application database writes
    -> DbInvalidationController.runMuted (listener batching only)
    -> CoreCrypto commit
    -> Events rows marked processed in a later database transaction
    -> process-local notification SharedFlows
```

The current design is safe only within assumptions made by a single process. In particular:

- the pending event batch is selected before any cross-process ownership is acquired;
- `lastEmittedEventId` exists only in the observing process;
- `runMuted` batches SQLDelight invalidations but is not an application database transaction;
- one CoreCrypto transaction currently covers a whole emitted event batch;
- both `IncrementalSyncWorker` and `EventProcessor` use broad `NonCancellable` scopes;
- several receiver paths perform or start network work while the CoreCrypto transaction is open;
- many conversation receiver branches discard handler failures and return success;
- the processed marker is written after CoreCrypto commit and outside an atomic application
  transaction, so crash recovery must be explicitly idempotent;
- notification creation, edit, delete, and seen intent is held by singleton `MutableSharedFlow`s;
- local SQLDelight listeners are not invalidated by writes from another process.

### Current Apple storage state

For an account `<domain>/<user-id>`, Kalium currently derives separate roots for:

```text
<root>/<domain>/<user-id>/storage/    Kalium user database and files
<root>/<domain>/<user-id>/proteus/    Proteus CoreCrypto store
<root>/<domain>/<user-id>/mls/        MLS CoreCrypto store, below a client-specific directory
<root>/global-storage/                Kalium global database
```

The caller supplies `<root>` to `CoreLogic`; Kalium does not currently express an App Group
contract or an NSE existing-state-only mode. On Apple, the user database uses WAL and up to 32
reader connections, but no explicit busy-timeout policy is configured. Database construction can
create directories, create schemas, run migrations, and insert `SelfUser`.

Proteus and MLS are separate physical CoreCrypto stores. The combined transaction order is already
Proteus before MLS and must be preserved.

`ApplePersistenceConfig` currently contains only `serviceName`, and `KeychainSettings` is created
with only that value. There is no explicit keychain access group or accessibility class. Apple user
and global SQLDelight databases are currently opened without a passphrase, which makes App Group
file-protection policy a security review item before production NSE use.

### Existing CoreCrypto dependency assumption

Kalium currently declares both platform CoreCrypto `9.3.4` artifacts and the unified KMP artifact
`9.3.3.4-kmp`.

For this plan, the Apple dependency used by the current iOS application is assumed to provide the
required process-safe transaction lifecycle. There is no planned CoreCrypto implementation
milestone. If implementation later disproves the assumption, that is a dependency blocker and a
separate CoreCrypto task rather than an expansion of the Kalium milestone in progress.

## Decision

Implement the feature as ordered milestones. No milestone may weaken the following invariants:

1. Crypto state is serialized across processes before transaction-scoped state is read.
2. Main sync and NSE use the same event-processing engine and receiver implementation from a
   lower-level shared module.
3. Pending events are queried from durable storage after acquiring the account event lock.
4. Delivery is at-least-once; duplicate crypto work and application persistence are idempotent.
5. Network and unbounded work do not run while event or crypto locks are held.
6. NSE processing has an explicit deadline, event limit, and safe cancellation boundaries.
7. NSE mode opens existing account state only and returns a typed main-app-required result when
   creation, migration, credential refresh, or repair is needed.
8. Locks are account or database scoped; different accounts remain independent.
9. Effects that must survive a crash or cross a process boundary are durable.
10. Wire iOS has one NSE processing path backed by Kalium; there is no legacy implementation or
    runtime rollout switch.
11. The NSE binary does not depend on `:logic` and does not construct `CoreLogic`,
    `UserSessionScope`, continuous sync, or the main-app observer graph.

Sharing event processing does not mean sharing the application lifecycle. The main application
continues to own full, continuous sync: WebSocket/push gathering, catch-up, sync-state publication,
and long-lived observers. The NSE invokes the same processing engine through a bounded one-shot
facade and performs only the input acquisition agreed in Milestone 0. It does not start full sync.

The shared engine belongs below `:logic` in the dedicated KMP module
`:domain:event-processing`. `:logic` and a lightweight NSE facade depend on that module; the event
processing module must not depend on `:logic`. It owns the event contracts, event source, batch
coordinator, dispatcher, receiver contracts, and processing logging. Milestone 2 keeps the existing
concrete receiver and handler implementations in `:logic` behind those contracts to avoid changing
their behavior while the boundary is established. Before the NSE facade is introduced in Milestone
5, the concrete handler closure required by the agreed first-release slice moves below `:logic` so
the app and NSE use the same implementation.

### Global lock order

Incoming event processing acquires locks in this order and releases them in reverse order where
possible:

```text
1. account event-processing lock
2. Proteus CoreCrypto database lock, when Proteus is needed
3. MLS CoreCrypto database lock, when MLS is needed
4. Kalium user-database transaction
```

Code holding a crypto lock must never acquire the account event lock. Network work must happen
before lock acquisition or be represented as a durable side effect for later execution.

### Target receive flow

```text
Main app :logic                                Lightweight NSE facade
continuous sync lifecycle                      bounded one-shot lifecycle
        |                                               |
        +---- fetch/parse and persist by event ID ------+
                                |
                    :domain:event-processing
                    shared processing engine
                                |
                    acquire account event lock
                                |
                 query authoritative pending Events rows
                                |
                     process one ordered event at a time
                                |
              crypto + application persistence + durable effects
                                |
              mark processed only after required commits succeed
                                |
                       release all locks and return
```

Because CoreCrypto and SQLDelight cannot share one atomic transaction, the coordinator is an
at-least-once state machine. The default NSE transaction granularity is one event. Every crash point
between application commit, CoreCrypto commit, and the processed marker must converge on retry.

## Milestones

### Milestone 0 - Define the first NSE slice

Goal: agree exactly what the first release will and will not do before production code changes.

Milestone 0 produces a small, reviewable feature contract. It contains no lock or event-processing
implementation.

Work:

- Choose the input for the first release: inline push payload, bounded pending-event fetch, or both.
- List the event and notification types supported in the first release. Unsupported types remain
  pending for the main app or return a typed main-app-required result.
- Set the processing budget contract: deadline, safety margin, maximum events, and lock-contention
  behavior.
- Decide which network work may happen before processing and which receiver work is deferred to the
  main app or represented as a durable side effect.
- Agree the App Group root, keychain access group and accessibility, existing-user migration owner,
  and first-release authentication refresh policy.
- Define the Swift mapping for completed, nothing-to-process, busy, deadline, main-app-required, and
  failed outcomes.
- Define the minimum privacy-safe telemetry required before dogfood.

Exit gate:

- a one-page first-release contract is accepted;
- supported inputs, events, notifications, budgets, and fallback behavior are explicit;
- shared storage, keychain, authentication, and Swift result contracts are explicit;
- every excluded behavior has a defined main-app fallback;
- no production behavior has changed.

### Milestone 1 - Behavior-preserving event-processing extraction

Repository: Kalium.

Goal: extract the event-processing body from `IncrementalSyncWorker` in place, without changing
behavior.

Work:

- Move the current decrypt/process/flush/mark sequence into one internal component with the smallest
  interface needed by `IncrementalSyncWorker`.
- Keep the component internal to `:logic` for this milestone; moving it and its dependencies to the
  shared module is Milestone 2.
- Continue passing the already emitted `streamData.eventList` into the extracted component.
- Preserve one combined CoreCrypto transaction for the complete emitted batch.
- Preserve Proteus-before-MLS transaction ordering.
- Preserve the current broad `NonCancellable` boundaries.
- Preserve `DbInvalidationController.runMuted`, pending-side-effect flushing, and processed-marker
  timing exactly as they are.
- Keep `IncrementalSyncWorker` responsible for gathering events and publishing sync state while it
  delegates only the existing processing body.
- Move or add characterization tests that prove the extracted path behaves like the current worker.

Explicitly out of scope:

- process or account locks;
- a direct pending-event DAO query;
- deadlines, budgets, lock policies, or new result types;
- per-event transaction granularity;
- SQLDelight transaction changes;
- cancellation or `NonCancellable` changes;
- receiver error-contract changes;
- network or side-effect reorganization;
- Gradle module moves or dependency-boundary changes;
- public NSE APIs or Apple-specific code.

Exit gate:

- `IncrementalSyncWorker` delegates the current processing body to the extracted component;
- transaction count, event order, side-effect flushing, error propagation, and processed markers are
  unchanged;
- existing incremental-sync tests remain green;
- no public API, schema, Apple configuration, or externally observable behavior changes.

### Milestone 2 - Move shared event processing below `:logic`

Repository: Kalium.

Goal: move the characterized processing path into a dedicated KMP module that the main app and NSE
can reuse without making the NSE depend on the full `:logic` graph.

This is a module-boundary refactor only. Milestone 1 deliberately extracts the behavior in place so
this move is mechanical and protected by characterization tests.

Work:

- Use `:domain:event-processing` as the shared event-processing module.
- Move the event model and delivery contracts, event source, event dispatcher, receiver contracts,
  processing logger, and the Milestone 1 batch coordinator below `:logic`.
- Introduce ports for crypto transactions, invalidation muting, per-event processing, processed
  markers, side-effect flushing, failure mapping, and processing observation.
- Keep the existing concrete receiver and handler implementations plus main-app lifecycle
  composition in `:logic` for this behavior-only move. They implement the shared contracts; no
  second implementation is created for the NSE.
- Move the Milestone 1 characterization tests with the component and preserve their assertions.
- Add a build-time dependency guard that prevents the event-processing module from depending on
  `:logic`.

Explicitly out of scope:

- changing the processing sequence or any behavior characterized in Milestone 1;
- constructing a public NSE API or Apple framework;
- `CoreLogic`, `UserSessionScope`, sync-state, WebSocket, call, analytics, worker, or observer
  initialization in the shared module;
- process locks, deadlines, per-event transactions, durable outbox behavior, storage configuration,
  or cancellation changes.

Exit gate:

- `:logic` depends on and uses `:domain:event-processing` for the extracted processing path and
  event dispatch;
- `:domain:event-processing` has no dependency on `:logic` and its build fails if one is added;
- event models, event source, dispatcher, receiver contracts, processing logging, and batch
  coordination are compiled by `:domain:event-processing`, not `:logic`;
- concrete receiver and handler behavior remains in `:logic` behind shared contracts until the
  supported first-slice closure is moved as part of Milestone 5;
- transaction count, ordering, cancellation, invalidation muting, side-effect flushing, marker
  timing, and error propagation remain unchanged;
- module tests and existing incremental-sync tests pass on the supported targets;
- cross-module contracts are marked `InternalKaliumApi`; no exported `KaliumLogic`/Apple product
  API, schema, Apple configuration, or externally observable behavior changes.

#### Milestone 2 receiver extraction status: MLS receiving Slice 2

MLS receive unpacking is now below `:logic` as a behavior-preserving module-boundary slice:

- `KaliumSyncException` retains its package and FQCN and is owned by `:domain:event-processing`.
- The `PendingProposalScheduler` contract retains its package and FQCN and is owned by
  `:domain:messaging:receiving`. `PendingProposalSchedulerImpl` and its eager coroutine, timer,
  transaction, and repository graph remain in `:logic`.
- `MLSMessageFailureHandler` and its resolution model retain their package and FQCN and are owned by
  `:domain:messaging:shared`, because receiving and non-receiving MLS consumers share the complete
  normalization and classification table. Its characterization tests run in that module.
- `MLSMessageUnpacker`, `MLSMessageUnpackerImpl`, and their characterization tests retain their
  package and FQCN and are owned by `:domain:messaging:receiving`. The implementation depends only
  on `ConversationProtocolGetter`, `SubconversationGroupInfoProvider`, `MLSMessageDecryptor`,
  `PendingProposalScheduler`, and `ProtoContentDecoder`.
- Main-app composition supplies the same existing objects: the broad conversation repository as the
  protocol getter, the stable process-local subconversation repository, the observable MLS
  repository wrapper as the decryptor, the existing eager scheduler implementation, and the
  existing protobuf mapper as the decoder. Successful decrypts therefore still pass through the
  observable crypto-state hook, while failures do not notify it.

This completed slice adds no NSE facade or runtime wiring and does not make MLS receiving ready to
instantiate in an NSE. The subconversation lookup is still process-local; NSE use requires durable
`(conversationId, subconversationId) -> groupId` state. Pending proposals still require an ownership
decision, durable outbox, and main-app executor design; the NSE must neither construct the current
scheduler implementation nor replace it with a no-op. The CoreCrypto cross-process lock is not part
of this slice. The broad protobuf encoder/mapper and `AssetMapper` graph remain in `:logic`, with the
receiving unpacker consuming only the focused decoder contract. New-message orchestration has since
moved below `:logic` in the next receiver-extraction slice.

#### Milestone 2 receiver extraction status: new-message orchestration slice

New-message orchestration is now below `:logic` as a behavior-preserving ownership refactor:

- `NewMessageEventHandler`, `NewMessageEventHandlerImpl`, and their complete test suite retain their
  package and FQCNs and are owned by `:domain:messaging:receiving`. Only the public visibility and
  `@InternalKaliumApi` annotations required for cross-module composition were added.
- The handler retains direct dependencies on `ProteusMessageUnpacker`, `MLSMessageUnpacker`,
  `ApplicationMessageHandler`, and `selfUserId`. The broad legal-hold, stale-epoch, and MLS-reset
  graphs cross the boundary only as
  `suspend (MessageUnpackResult.ApplicationMessage, Boolean) -> Either<CoreFailure, Unit>`,
  `suspend (CryptoTransactionContext, ConversationId, SubconversationId?, Instant?) -> Either<CoreFailure, Unit>`,
  and `suspend (ConversationId, CryptoTransactionContext) -> Either<CoreFailure, Unit>` callbacks.
  The existing self-deletion and confirmation-delivery callbacks retain their meaning.
- `UserSessionScope` supplies the same actions through `legalHoldHandler::handleNewMessage`,
  `staleEpochVerifier::verifyEpoch`, and the existing ephemeral self-deletion and
  confirmation-delivery actions. It captures `resetMlsConversation` once per handler construction
  and supplies an unambiguous lambda invoking that captured instance with
  `(conversationId, transactionContext)` before converting the result to `Either`.
- Proteus and MLS failure classification, exact decryption-error payloads, parent-only MLS failure
  persistence, transaction wrapping, batch order and per-item logger reset, legal-hold/content/
  insertion-side-effect order, pending-side-effect flushing, ignored callback results, exceptions,
  and cancellation are unchanged. The complete moved suite uses local event/transaction fixtures
  and focused callback recorders and adds characterization for reset exclusivity, exact flush
  delegation, processing order, and exception/cancellation propagation.

This does not make NSE runtime composition ready. Legal hold, stale-epoch recovery, reset/rejoin,
confirmation delivery, self deletion, and pending-side-effect durability still need explicit NSE
ownership/adapters or a durable action/outbox design. Durable subconversation mapping,
pending-proposal ownership/outbox/execution, and cross-process CoreCrypto locking remain separate
work. The larger conversation lifecycle handlers still remain in `:logic` and block the complete
`ConversationEventReceiverImpl` move.

#### Milestone 2 receiver extraction status: grouped small conversation-state lifecycle slice

The member-change and MLS-reset handlers are now below `:logic` as one behavior-preserving boundary:

- `MemberChangeEventHandler`, `MemberChangeEventHandlerImpl`,
  `MLSResetConversationEventHandler`, and `MLSResetConversationEventHandlerImpl` retain their
  packages and FQCNs and are owned by `:domain:messaging:receiving`. Their complete test suites move
  with them; only the visibility and `@InternalKaliumApi` annotations needed for cross-module
  composition are added.
- Member change keeps direct access to the focused `ConversationLifecycleEventRepository`,
  `PersistMessageUseCase`, and self user ID. Fetch-if-unknown crosses the boundary only as
  `suspend (CryptoTransactionContext, ConversationId) -> Either<CoreFailure, Unit>`.
  `UserSessionScope` evaluates `fetchConversationIfUnknownUseCase` once per handler construction,
  captures that exact instance, and invokes it with the original two arguments so
  `ConversationSyncReason.Other` remains the default.
- MLS reset depends on the focused `MLSResetEventRepository` operations for leaving a group,
  checking the replacement group, and updating the local group ID/epoch/state, plus a focused
  `suspend (ConversationId) -> Unit` call-termination callback. The existing `MLSConversationRepository`
  extends that contract, and `UserSessionScope` supplies the exact observable wrapper and captured
  `EndCallOnMLSResetUseCase`; successful leave operations therefore retain the existing crypto-state
  hook without a duplicate data source or adapter.
- Member role-read/fetch/update/promotion-persistence order, ignored fetch and persistence results,
  muted/archive behavior, unsupported-variant skipping, logs, exact promotion payload, and
  exception/cancellation propagation are unchanged. MLS call-termination/leave/check/conditional-epoch/update order,
  the null-MLS short circuit after call termination, ignored leave and update results, failed-check fallback, exact
  state/epoch values, and thrown exception/cancellation behavior are likewise unchanged.

This slice adds no NSE facade, runtime composition, lock, retry, queue, outbox, or CoreCrypto
transaction change. At completion of this slice, `MLSWelcomeEventHandler` and the larger
new-conversation, delete, member-join, and member-leave handlers remained in `:logic`; they have
since moved in the focused slices below.

#### Milestone 2 receiver extraction status: protocol-update conversation slice

Protocol-update event handling is now below `:logic` as a behavior-preserving ownership refactor:

- `ProtocolUpdateEventHandler`, `ProtocolUpdateEventHandlerImpl`, the private deleted-conversation
  failure classifier, and the complete test suite retain their packages and FQCNs and are owned by
  `:domain:messaging:receiving`. Only the public visibility and `@InternalKaliumApi` annotations
  required for cross-module composition were added.
- The handler keeps the receiving-owned `SystemMessageInserter` directly. The broad logic-owned
  update use case and call repository cross the boundary only as
  `suspend (CryptoTransactionContext, ConversationId, Conversation.Protocol, Boolean) -> Either<CoreFailure, Boolean>`
  and `suspend () -> Boolean` callbacks.
- `UserSessionScope` captures exactly one `UpdateConversationProtocolUseCase`, the existing stable
  `CallRepository`, and one `SystemMessageInserter` per handler construction. The update callback
  forwards the original transaction, conversation, protocol, and `localOnly = true`; the call
  callback retains `establishedCallsFlow().first().isNotEmpty()` and therefore observes the first
  flow emission on every successful update, including non-`MIXED` updates.
- Update-before-message-before-call-query order, the original `&&` short circuit, optional
  during-call message arguments, deleted-conversation classification and informational log,
  success/failure event logging, `Either` mapping, exception propagation, and cancellation
  propagation are unchanged. The moved suite retains every prior test and adds narrow order,
  argument, first-emission, skip, classification, exception, and cancellation characterization.

This slice adds no NSE runtime wiring, CoreCrypto lock, retry, queue, durable action/outbox, rollout
switch, or receiver move. At completion of this slice, `MLSWelcomeEventHandler`,
`NewConversationEventHandler`, `DeletedConversationEventHandler`, `MemberJoinEventHandler`, and
`MemberLeaveEventHandler` remained in `:logic`; they have since moved in the focused slices below.
`ConversationEventReceiverImpl` remains logic-owned.

#### Milestone 2 receiver extraction status: MLS-welcome conversation slice

MLS-welcome event handling is now below `:logic` as a behavior-preserving ownership refactor:

- `MLSWelcomeEventHandler`, `MLSWelcomeEventHandlerImpl`, their private helpers and outcome
  constants, and the complete test suite retain their package and FQCNs and are owned by
  `:domain:messaging:receiving`. Only the public visibility and `@InternalKaliumApi` annotations
  required for cross-module composition were added.
- The receiving-owned, focused `MLSWelcomeEventRepository` extends the already-lower
  `ConversationProtocolGetter` and exposes only group-state update and conversation-details
  observation in addition to protocol lookup. The existing logic-owned `ConversationRepository`
  extends that contract and supplies its existing implementations; no broad repository, data
  source, adapter, or duplicate state moved below `:logic`.
- Fetch-if-unknown, one-to-one resolution, key-package refill, CRL checking and persistence, and
  external-commit rejoin cross the boundary only as focused suspend callbacks. `UserSessionScope`
  captures the same conversation repository, resolver, refill use case, CRL checker, CRL
  repository, join use case, and fetch use case exactly once and in their original constructor
  evaluation order. The fetch and join callbacks retain their original two-argument calls and
  defaults; refill adapts only `Success`/`Failure` to `Either`.
- MLS-null short-circuiting, fetch/process/CRL/establish/observe/resolve order, `Flow.first`, CRL URL
  order and ignored results, orphan-welcome classification and recovery, no-conversation skipping,
  refill logging/ignoring, event outcomes, exact log text, returned-failure short circuits, wrapped
  MLS failures, exceptions, and cancellation remain unchanged. The logic-only
  `CryptoTransactionContext.wrapInMLSContext` behavior is reproduced as a private local helper.

This slice adds no NSE runtime wiring, CoreCrypto lock, retry, queue, durable action/outbox, rollout
switch, receiver move, or other lifecycle handler. `NewConversationEventHandler`,
`DeletedConversationEventHandler`, `MemberJoinEventHandler`, and `MemberLeaveEventHandler` are the
remaining concrete conversation lifecycle handlers in `:logic`; `ConversationEventReceiverImpl`
also remains logic-owned. `UserEventReceiverImpl` is explicitly outside this extraction goal.

#### Milestone 2 receiver extraction status: grouped new/delete conversation slice

New-conversation and deleted-conversation event handling are now below `:logic` as one
behavior-preserving ownership refactor:

- `NewConversationEventHandler`, `DeletedConversationEventHandler`, their implementations, and all
  13 pre-existing named tests retain their packages and FQCNs and are owned by
  `:domain:messaging:receiving`. Only the public visibility and `@InternalKaliumApi` annotations
  required for cross-module composition were added.
- Both handlers share the focused receiving-owned `ConversationEventUserRepository`, exposing only
  unknown-user fetch and single-user observation. New-conversation uses the five-operation
  `NewConversationSystemMessagesCreator`; deletion uses `DeletedConversationEventRepository` for
  one conversation lookup. The broad logic-owned repositories and system-message creator extend
  these contracts and reuse their existing implementations without adapters or duplicate state.
- `ConversationSyncReason` and its public artifact ownership remain unchanged in `:logic`.
  New-conversation crosses a focused two-argument event-persistence callback; main-app composition
  invokes the captured persistence use case with `reason = ConversationSyncReason.Event`, preserving
  exact event semantics without exposing the reason type below `:logic`.
- New-conversation persistence, one-to-one resolution, and conversation-type mapping cross as the
  three focused callbacks. `UserSessionScope` captures lifecycle repository, user repository,
  self-team provider, system-message creator, resolver, and persistence use case exactly once in
  their original written evaluation order. The mapper remains composed from the existing internal
  `ConversationResponse.toConversationType` extension.
- Deleted-conversation deletion crosses as one focused callback. Composition captures user
  repository, lookup repository, notification manager, deletion use case, hook notifier, and self
  user ID exactly once in their original written evaluation order; constructing the deletion use
  case remains after the separate lookup-repository capture.
- Self-team lookup, persistence, mapping/resolution, modified-date update, unknown-user fetch, and
  the five independently ignored system-message results retain their exact order and failure,
  exception, and cancellation behavior. Conversation lookup, deletion, `Flow.firstOrNull`, meeting
  notification suppression, success/skip/failure logging, and the normally unconditional final
  deletion hook likewise remain unchanged.
- The moved suites use local fixtures and focused recorders and add exact identity, argument, order,
  mapper-count, millisecond-time-bound, returned-failure, first/empty/null flow, ignored-message,
  ordinary-exception, and cancellation characterization.

This slice adds no NSE runtime wiring, CoreCrypto lock, retry, queue, durable action/outbox, async
redesign, rollout switch, or receiver move. `MemberJoinEventHandler` and
`MemberLeaveEventHandler` are the only remaining concrete conversation handlers in `:logic`;
`ConversationEventReceiverImpl` remains logic-owned. Durable/asynchronous deletion and main-app
side-effect execution remain future work. `UserEventReceiverImpl` is explicitly outside this goal.

#### Milestone 2 receiver extraction status: grouped member-join/member-leave conversation slice

Member-join and member-leave event handling are now below `:logic` as one behavior-preserving
ownership refactor:

- `MemberJoinEventHandler`, `MemberLeaveEventHandler`, their implementations, and all 25
  pre-existing named tests retain their packages and FQCNs and are owned by
  `:domain:messaging:receiving`. The handler contracts remain ordinary interfaces; only required
  cross-module public visibility and `@InternalKaliumApi` annotations were added.
- Both handlers reuse the receiving-owned lifecycle and message persistence contracts, the existing
  focused system-message contract, `ConversationProtocolGetter`, and `MLSResetEventRepository`.
  Focused join/leave conversation-user contracts add only the remaining lookup, active one-to-one,
  mark-deleted, and team-membership operations. The broad logic repositories extend them directly;
  no adapter, duplicate state, or broad repository implementation moved.
- Fetch-conversation, legal-hold refresh, lazy call-client update, and self-team lookup cross focused
  callbacks. `ConversationSyncReason` remains logic-owned, and the captured fetch use case keeps its
  two-argument invocation and default `ConversationSyncReason.Other`. The existing public call-update
  use-case type also remains logic-owned.
- `UserSessionScope` captures each original constructor argument exactly once in original written
  order. The existing call-update `Lazy` is captured once and forced only after successful deletion.
  MLS cleanup uses the same event transaction and nullable MLS context in unified and legacy crypto
  modes.
- Join keeps fetch-first behavior, member-persistence result ownership, exact type branches,
  one-to-one activation, warning/member-added message construction, UUID fallback, ignored results,
  logging, exceptions, and cancellation. Leave keeps mark/delete/call/MLS/fetch/count/message/legal
  order, ignored results, zero-count behavior, self-only MLS cleanup, team classification, legal-hold
  result ownership, exact messages, logging, exceptions, and cancellation.
- The moved tests use focused local fixtures and preserve all original names and intent while adding
  exact order, identity, arguments, result-ownership, branch, exception, and cancellation
  characterization.

This slice adds no NSE runtime wiring, CoreCrypto process lock, retry, queue/outbox, rollout flag,
async side-effect redesign, or receiver routing. `ConversationEventReceiverImpl` is now the
remaining conversation extraction target. `UserEventReceiverImpl` remains explicitly outside this
goal.

#### Milestone 2 receiver extraction status: conversation receiver slice

Conversation receiver orchestration is now below `:logic` as a behavior-preserving ownership
refactor:

- `ConversationEventReceiverImpl` and its focused tests retain their package and FQCN and are owned
  by `:domain:messaging:receiving`. The `ConversationEventReceiver` contract remains in
  `:domain:event-processing`.
- The implementation remains a normal class with the exact constructor parameter list, order, and
  types. Only the public class/constructor visibility and `@InternalKaliumApi` annotation needed by
  cross-module main-app composition were added. `UserSessionScope` keeps constructing the same FQCN
  from the same dependency expressions, so composition and constructor evaluation are unchanged.
- The exact branch order, handler arguments, ignored-versus-propagated `Either` ownership,
  `Right(Unit)` results, exception/cancellation propagation, and one-call pending-side-effect flush
  behavior are unchanged. The moved suite preserves all 19 original test names and uses focused
  local fixtures while adding complete route, result-ownership, reference-identity, flush, and
  thrown-failure characterization.
- No handler interface or implementation, event contract, module dependency, `UserEventReceiverImpl`,
  or runtime wiring changed in this slice. This completes the conversation-handler ownership
  extraction goal; `UserEventReceiverImpl` remains logic-owned and explicitly outside it.

Ownership extraction is not NSE runtime readiness. Shared-storage bootstrap, existing-state-only
session construction, the NSE facade, durable side effects/outbox execution, durable
subconversation and pending-proposal coordination, cross-process CoreCrypto locking, rollout, and
retry/async redesign remain separate milestones.

#### Deferred feature-configuration boundary follow-up

After the shared-storage milestone, move `FeatureConfigRepository`, its persistence implementation,
and the shared feature-configuration handlers into a neutral `:domain:feature-config` module. Slow
sync and any retained compatibility receiver must consume the same implementations from that
module. This is a later ownership cleanup, not part of Milestone 3, and must not expand the NSE event
slice or pull `UserEventReceiverImpl` into the extraction.

### Milestone 3 - Apple shared storage, keychain, and existing-state-only mode

Repository: Kalium.

Goal: let the main app and NSE open the same prepared account safely without allowing the extension
to create or repair identity state.

Work:

- Define an explicit Apple shared-root configuration supplied from the App Group container.
- Preserve existing account directory compatibility; use generic lock filenames under the account
  root and never include raw IDs in lock names or logs.
- Extend `ApplePersistenceConfig` with an optional keychain access group and an explicit
  accessibility policy.
- Implement and verify main-app-owned migration from legacy keychain entries to shared entries.
  The NSE only reports that main-app preparation is required.
- Add `MainAppReadWrite` and `NotificationExtensionExistingStateOnly` open modes through global DB,
  user DB, CoreCrypto providers, credentials, and session construction.
- In NSE mode, detect missing stores, missing keys, schema mismatch, migration need, and incompatible
  versions before any creation or destructive recovery.
- Use existing valid authentication in the first release. Unless a separate auth-refresh design is
  approved, expired credentials return a typed main-app-required result.
- Review App Group file data protection and whether Apple SQLDelight databases require encryption
  before they contain a durable notification outbox.

Exit gate:

- both process configurations resolve byte-for-byte identical account, user DB, Proteus, MLS, and
  lock paths;
- both targets resolve the same keychain entries after main-app migration;
- extension mode cannot create directories, databases, identities, secrets, or run migrations;
- before-first-unlock and migration-required cases are typed and non-destructive;
- two accounts remain isolated.

### Milestone 4 - Durable notification and side-effect outbox

Repository: Kalium.

Goal: make notification intent and required follow-up work durable and idempotent.

Work:

- Add a dedicated outbox table rather than extending `PendingActions`, whose current key and payload
  model cannot express event provenance, leases, acknowledgement, or notification data.
- Separate authoritative receiver mutations from follow-up effects. Sender verification and the
  message hard-delete or tombstone remain synchronous event-processing work; asset-file cleanup,
  notification updates, and any hook that is classified as non-authoritative may become outbox work.
- For every effect moved out of the receiver, commit the authoritative database mutation and its
  outbox row in one targeted database transaction before the event may be marked processed. This
  does not change the crypto/event transaction granularity reserved for Milestone 6.
- Capture every value needed by a deferred effect before destructive mutation. In particular, write
  the asset ID and stable message/conversation target into the outbox before hard-deleting a message
  that may be their only source.
- Store source event ID, effect type, stable target/payload, creation time, state, claim owner,
  claim expiry, and deduplication key.
- Enforce uniqueness by source event, effect type, and deduplication key.
- Add transactional claim, acknowledge, release, and expired-claim recovery operations.
- Change receiver notification scheduling to persist effects. Existing `MutableSharedFlow` APIs may
  remain as same-process adapters but are no longer authoritative.
- Add a main-app executor for slow or app-only effects such as local asset cleanup. Notification
  effects may be claimed by the bounded NSE request when required for its response, with the main
  app retaining recovery/cleanup ownership; consumers must use the same claim/acknowledgement model.
- Persist network/long-running follow-up intents required by the agreed first NSE slice when they are
  needed for retry correctness.

Exit gate:

- retrying an event creates one logical effect;
- two claimers cannot own the same effect;
- crashes before acknowledgement are recoverable;
- a processed delete event always has both its authoritative message mutation and every required
  deferred-effect row durably committed;
- slow asset cleanup does not extend event or crypto lock duration, and losing the original message
  row does not lose the asset-cleanup target;
- edit, delete, seen, and the agreed first-slice notification types survive process termination;
- existing main-app notification behavior remains compatible.

### Milestone 5 - Public bounded Kalium NSE API

Repository: Kalium.

Goal: expose a small Swift-friendly one-shot processor from its own lightweight artifact, without
linking the full `:logic` module or exporting incremental-sync internals.

Work:

- Before constructing the NSE facade, move the concrete receivers and handlers required by the
  Milestone 0 event slice below `:logic`; the main app must switch to those same implementations.
- Add a dedicated KMP `:nse` module that depends on `:domain:event-processing` and only the
  lower-level modules required by the bounded path. It must not depend on `:logic`.
- Export an Apple framework named `KaliumNSE` from `:nse`; the NSE target does not link
  `KaliumLogic`.
- Add a lightweight notification-extension scope that initializes only existing account metadata,
  credentials, databases, CoreCrypto clients, event persistence, the shared processor, and outbox.
- Accept inline payloads and a bounded pending-event fetch, using durable event IDs and
  `INSERT OR IGNORE`.
- Expose concrete, non-generic public input, budget, result, failure-reason, notification, and
  acknowledgement types in line with ADR 7.
- Define distinct outcomes for completed, nothing to process, busy, deadline reached,
  main-app-required, and failed. Cross-process contention starts producing `Busy` in Milestone 6.
- Claim effects after event processing and return a batch token for acknowledgement.
- Make Swift task cancellation safe at current processing boundaries.
- Update ABI dumps and add a changelog fragment for the new public surface.
- Do not wire the API into the production Wire iOS NSE target yet.

Exit gate:

- inline, fetch, empty, deadline, cancellation, missing-state, and repeated-invocation API tests pass
  on Apple;
- the `:nse` dependency graph contains no `:logic`, `CoreLogic`, or `UserSessionScope` dependency;
- the scope does not start calls, analytics, WebSockets, background workers, or unrelated observers;
- no generic `Either` is exposed to Swift.

### Milestone 6 - Cross-process event coordination

Repository: Kalium.

Goal: make the extracted processor safe for real concurrent main-app and NSE use.

Work:

- Add an injectable account-scoped `EventProcessingLock` with process-local and OS-backed protection
  on Apple, plus deterministic test fakes.
- Add a one-shot DAO/repository query for unprocessed events ordered by persistent `Events.id`.
- Acquire the event lock before the authoritative pending-event query. Do not use the batch emitted
  earlier by the process-local `Flow` as processing authority.
- Add execution mode, lock policy, maximum event count, monotonic deadline, structured report, and
  typed stop reasons to the shared processor.
- Process one event per crypto transaction initially and preserve Proteus-before-MLS ordering.
- Map observable CoreCrypto contention and cancellation into `Busy` or deadline outcomes instead of
  an unknown failure.
- Replace receiver `Unit`/swallowed-error behavior with explicit applied, duplicate, terminal-skip,
  retryable-failure, and main-app-required outcomes where needed by the NSE slice.
- Add a real per-event SQLDelight transaction boundary or document and test each temporarily
  non-transactional mutation.
- Narrow `NonCancellable` to the minimum commit/rollback boundary and permit cancellation before lock
  acquisition and between events.
- Move network work outside event and crypto locks, using the durable outbox for required follow-up
  work.

Exit gate:

- two process-style coordinator instances cannot process the same event concurrently;
- pending events are always queried after lock acquisition;
- duplicate Proteus and MLS events complete successfully and are marked processed;
- adjacent Proteus and ordered MLS fixtures converge under concurrent app/NSE execution;
- cancellation and fault injection at every commit boundary leave recoverable state;
- different accounts can make progress concurrently;
- no network mock is called while an event or crypto lock is held.

### Milestone 7 - Wire iOS integration

Repository: Wire iOS.

Goal: add the Wire iOS NSE integration and its complete request lifecycle.

Work:

- Add the NSE entry point, request-handler tracking, timeout callback, and exactly-once
  content-handler completion.
- Resolve the account and configure the shared App Group and keychain entitlements.
- Link `KaliumNSE`, not `KaliumLogic`, and invoke its one-shot processor as the single NSE
  processing path.
- Map typed Kalium results to notification content, safe pass-through, or open-main-app behavior.
- Persist acknowledgement or release unused claims before calling Apple's content handler.
- Keep push-channel ownership as an optimization and liveness aid, not a correctness lock.
- Add privacy-safe duration, contention, result, and count telemetry.

Exit gate:

- app/NSE same-event, send/receive, Proteus/MLS, repeated request, timeout, missing entitlement, and
  before-first-unlock integration tests pass;
- safe pass-through behavior for typed no-work and failure results is tested.

### Milestone 8 - Cross-process database invalidation

Repositories: Kalium, then Wire iOS wiring as needed.

Goal: make a running or relaunched main app promptly observe user DB writes made by the NSE.

Work:

- Persist an account-scoped invalidation generation and affected SQLDelight query keys.
- Post a Darwin notification after commit; on receipt or app activation, consume generations newer
  than the last locally observed generation.
- Extend `DbInvalidationController`/`MutedSqlDriver` so a local flush can publish externally, while
  externally received invalidations are not republished.
- Define a documented account-scope reload fallback if arbitrary SQLDelight key invalidation is not
  safe.

Exit gate:

- running observers refresh after NSE writes;
- missed Darwin notifications catch up from durable generations;
- there are no publish loops and accounts do not cross-invalidate;
- correctness remains independent of notification delivery.

### Milestone 9 - Hardening and release validation

Repositories: Kalium and Wire iOS. CoreCrypto remains an existing dependency.

Goal: demonstrate production safety and performance.

Work:

- Repeatedly run the full app/NSE stress and crash matrix for same events, adjacent Proteus messages,
  ordered MLS messages, process kills at every commit boundary, two accounts, low disk, migrations,
  and deadline contention.
- Review lock-file permissions, App Group data protection, keychain scope/accessibility, log privacy,
  and extension restrictions.
- Measure cold initialization, CoreCrypto open, lock acquisition and wait, one Proteus event, one MLS
  event, notification mapping, peak memory, and total NSE duration.
- Validate through internal builds and dogfood before production release. This is release
  validation, not a runtime feature-flag or dual-path rollout mechanism.
- Publish setup, entitlement, migration, lock-order, crash model, acknowledgement, telemetry, and
  troubleshooting documentation.

Exit gate:

- all correctness invariants hold under real multi-process stress;
- deadline safety margin and memory targets are agreed and met;
- corruption, crash, and notification-delivery dashboards support release decisions;
- safe push-content pass-through for no-work and failure results is tested before production.

## Delivery sequence

Keep changes reviewable and dependency ordered:

1. Define and accept the first NSE slice.
2. Extract current event processing without behavior changes.
3. Move the characterized event-processing core and contracts into `:domain:event-processing`
   without behavior changes.
4. Add Apple shared persistence and existing-state-only mode.
5. Add the durable outbox.
6. Add the public `:nse` API and `KaliumNSE` framework without production Wire iOS wiring.
7. Add cross-process event coordination.
8. Add the Wire iOS integration.
9. Add cross-process invalidation.
10. Complete release hardening.

Wire iOS must not invoke real shared-account event processing until cross-process coordination in
step 7 is complete.

## Validation per Kalium milestone

Run the narrowest affected tests during development, then the relevant repository gates:

```text
./gradlew :domain:event-processing:jvmTest -Djava.library.path=./native/libs
./gradlew :logic:jvmTest -Djava.library.path=./native/libs
./gradlew :data:persistence:jvmTest
./gradlew :data:persistence:verifySqlDelightMigration
./gradlew iosSimulatorArm64Test -PUSE_UNIFIED_CORE_CRYPTO=true
./gradlew detekt
./gradlew :logic:checkKotlinAbi -PUSE_UNIFIED_CORE_CRYPTO=true   # public API changes
./gradlew :nse:checkKotlinAbi -PUSE_UNIFIED_CORE_CRYPTO=true     # after :nse is introduced
```

Apple multi-process behavior requires process-based integration tests. Two coroutines, two
dispatchers, or two in-process database objects are useful additional tests but are not substitutes.

## Open decisions for Milestone 0

- Does the first release accept inline push payloads, fetch pending events, or support both inputs?
- Which event and notification types belong to the first release?
- What deadline safety margin, event limit, and contention behavior should the public budget use?
- Which receiver work is allowed in the extension, and which work must wait for the main app?
- What are the exact first-release notification effect types and rendering responsibilities?
- Is existing-valid-auth-only acceptable for the first release, or is a separate cross-process token
  refresh coordinator required?
- What should Wire iOS display for busy, deadline, main-app-required, and failed results?

## Implementation decisions for Milestones 3, 5, and 6

- Which concrete receiver and handler implementations are required by the Milestone 0 slice and
  therefore must move below `:logic` before the `:nse` module is introduced?
- Which POSIX advisory lock primitive and cancellation strategy will be used by the Kalium account
  event-processing lock?
- Does the Apple SQLDelight driver need an explicit busy timeout in addition to the event lock, and
  how should non-event writers behave under contention?
- Can all per-event application mutations run inside one SQLDelight transaction without crossing
  incompatible dispatcher or callback boundaries?
- What App Group file-protection and database-encryption policy is required for the unencrypted Apple
  user/global databases?
- Will the account directory layout remain compatible as-is, or will a separately planned migration
  introduce obfuscated account directory keys?

## Consequences

### Easier

- App and NSE can safely race, retry, and recover without relying on process ownership timing.
- Main sync and NSE behavior share one implementation and one test matrix.
- The NSE links a bounded facade instead of the full `:logic` framework and cannot accidentally
  construct the main-app session, observer, or continuous-sync graph.
- Notification intent and UI freshness have explicit cross-process mechanisms.
- Typed busy, deadline, and preparation outcomes let Wire iOS degrade safely.
- There is one production NSE path, so correctness does not depend on runtime flag state or legacy
  fallback selection.

### More difficult

- There are two levels of cross-process locking plus protocol-specific database locks, so lock order
  must be enforced in code review and tests.
- Moving the reusable processor and receivers below `:logic` requires a deliberate dependency and
  API boundary before NSE-specific work begins.
- Event processing becomes an explicit at-least-once state machine across two databases.
- Some current receiver work must be split into durable mutation and later network execution.
- Existing Apple keychain and path behavior requires migration and entitlement coordination.
- Cross-repository dependency sequencing and real process-based tests add delivery overhead.
