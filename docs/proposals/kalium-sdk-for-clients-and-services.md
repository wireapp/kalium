# Kalium SDK for client apps and backend services

| Field | Value |
| --- | --- |
| Status | Approved for experimental implementation |
| Date | 2026-07-14 |
| Owners | Kalium team |
| Reviewers | Calling, MLS/CoreCrypto, client platform, and PSTN service teams |
| Related decision | ADR 0010: Split the SDK by capability |

## Executive summary

Kalium is currently packaged mainly as a client SDK. Its public `:logic` module wires together
networking, local databases, message handling, calling, synchronization, backup, and background
work.

We now want to use Kalium in backend services. The first case is a PSTN service. Kalium will handle
the Wire side of the flow: authentication, conversation access, MLS state, call signalling, and
joining or leaving calls. A separate service will route media to and from the PSTN network.

The PSTN service does not need message history, a full conversation database, unread state, or
client background work. Today it cannot leave those parts out because they are wired into
`:logic`, `UserSessionScope`, and the current calling implementation.

This proposal changes Kalium from one large client setup into a set of reusable capabilities. Two
small composition modules will provide useful defaults:

- `:logic:client` for the existing full client SDK.
- `:logic:service` for headless services.

The existing `:logic` artifact will remain as a compatibility facade for client applications while
the code is moved in small steps.

This is not a PSTN fork. PSTN-specific routing stays outside the shared Kalium runtime.

### Implementation confirmation for the pre-review pass

The calling team has not confirmed the final public integration contract. Implementation therefore
proceeds with changeable APIs and these safe defaults:

- Start with a JVM service artifact and keep reusable capability contracts multiplatform.
- Own one runtime locally per Wire identity, with explicit `start()` and idempotent `close()`.
- Preserve existing call protocols rather than narrowing support without evidence.
- Keep active call state in memory by default.
- Require service-supplied durable session/credential, Wire client identity, CoreCrypto/MLS, event
  checkpoint, and idempotency state; required stores cannot be replaced by silent no-ops.
- Keep authenticated HTTP and WebSocket delivery, calling-event acknowledgement, decoding and
  decryption, AVS routing, and encrypted outgoing calling signalling in the service composition.
- Exclude chat/application-message history, unread and UI state, full client sync, call history,
  missed-call message persistence, paging, backup, and Android background work.

New tests, fixtures, and changes to existing tests are deferred until calling-team confirmation.
This implementation pass uses focused production compilation, static checks, dependency-graph
inspection, and ABI validation. It must not claim that the deferred or existing tests passed.

### Implementation record

The pre-review implementation completed the proposal phases as separate commits:

| Phase | Commit | Result |
| --- | --- | --- |
| 0 | `0bc2e039d008` | Accepted the experimental ADR and recorded the provisional contracts. |
| 1 | `9c0a45df184c` | Added storage-neutral durable event delivery and processing contracts. |
| 2 | `768c0913f673` | Added local/remote conversation context and protocol-state boundaries. |
| 3 | `7d36a1dbb97b` | Extracted headless calling runtime and optional calling-history behavior. |
| 4 | `f3860a54ce8b` | Added explicit headless service composition and PSTN host sample. |
| 5 | `e038b1d6315b` | Added the explicit full-client composition while retaining `:logic` compatibility. |
| 6 | Recorded by the final implementation commit | Adds ABI baselines, executable module-graph checks, operations guidance, release notes, and this deferred-validation record. |

Phase 5 intentionally retains the transitional `:logic:client -> :logic` dependency so existing
class locations, constructors, Apple exports, and ABI do not move without the deferred client
regression and performance suite. The ownership reversal and an Apple export for the new client
artifact remain required follow-up work after calling-team confirmation.

## Problem

### Current shape

The current `:logic` module is both:

1. The public SDK API.
2. The place where almost every Kalium feature is created and connected.

It depends on local persistence, messaging, calling, backup, cells, user storage, user network, and
background work. `UserSessionScope` creates user storage as part of the session. Calling also mixes
runtime call state with database state and message persistence.

This works for client apps because those apps normally need the full feature set. It creates
problems for backend services:

- A service gets code and dependencies it does not use.
- Message storage is hard to disable safely.
- Calling cannot be tested or run without large parts of `UserSessionScope`.
- Client-only behavior, such as creating missed-call messages, is part of the calling path.
- Service lifecycle, concurrency, and restart needs are different from a mobile app.
- Adding `serviceMode` checks would spread product choices through shared code.

### PSTN use case

A simplified PSTN call flow is:

```text
PSTN service request
        |
        v
Resolve Wire identity and conversation
        |
        v
Join the Wire conversation and call through Kalium
        |
        v
Exchange call signalling and media with AVS/SFT
        |
        v
External PSTN component routes media to the phone network
```

Kalium owns the Wire protocol side. It does not own phone numbers, PSTN routing rules, billing,
SIP trunks, or telecom provider logic.

## Goals

- Let backend services use calling without message history or full client sync.
- Keep one shared implementation for Wire networking, conversations, MLS, and calling.
- Keep the current client SDK behavior and public APIs working during migration.
- Make storage a choice for each capability instead of one global choice.
- Make event handling work without automatically storing messages.
- Make calling usable without constructing the full client `UserSessionScope`.
- Support future headless consumers such as bots, bridges, and automation services.
- Keep module dependencies clear and testable.

## Non-goals

- Implement PSTN or SIP routing inside Kalium.
- Replace AVS or CoreCrypto.
- Remove all durable state from service deployments.
- Redesign every Kalium feature at once.
- Expose low-level `Either` results from the public `:logic:*` APIs.
- Break existing clients just to reach the final module structure faster.

## Design principles

### Split by capability, not by product

Shared modules will represent capabilities such as event processing, conversation access, or call
runtime. They will not contain checks for Android, PSTN, bot, or service products unless the check
is truly platform-specific.

### Composition happens at the top

Lower modules must not know whether they are used by a client or a service. The top-level
`:logic:client` and `:logic:service` modules choose implementations and connect them.

### Do not hide dependencies

Calling must state what it needs through constructor parameters and small interfaces. It must not
reach into a large session scope to find databases, message repositories, or network clients.

### Separate live state from history

Active call state is needed to run a call. Call history is a client feature. These two concerns
must not share one required database interface.

### Separate event delivery from feature actions

Receiving and decoding an event is shared work. Storing a message is one optional action taken for
some events.

### Keep required protocol state safe

No message storage does not mean no storage. Client identity, credentials, MLS state, and delivery
state may need to survive restarts.

## Proposed architecture

Kalium keeps the current layer direction:

```text
core -> data -> domain -> logic
```

The proposed modules are grouped into three parts:

1. Shared foundations and adapters.
2. Feature capabilities.
3. Client and service composition modules.

### Target module map

```text
core/*
    common
    data
    cryptography
    libsodium
    logger
    util

data/network*
    Existing HTTP, WebSocket, API models, and network utilities.

data/session/api
    Session, credential, and Wire client identity contracts.
data/session/remote
    Authentication and client operations against the Wire backend.
data/session/local
    Existing persisted account, token, and client setup.

data/conversation/api
    Small contracts for conversation data used by domain features.
data/conversation/remote
    Fetch required conversation data from the backend on demand.
data/conversation/local
    SQLDelight-backed conversation data and cache for clients.

data/events/api
    Event source, envelope, checkpoint, and acknowledgement contracts.
data/events/websocket
    Read, decode, decrypt, acknowledge, and reconnect WebSocket events.

domain/event-processing
    Route decoded events to installed feature handlers.

domain/conversation-runtime
    Get conversation type, members, clients, protocol, and MLS context.
    Join or leave the conversation state needed for a live operation.
domain/conversation-sync
    Full client conversation synchronization and local catalog.

domain/calling
    Existing low-level AVS bridge.
domain/calling-notifications
    Existing AVS notification processing.
domain/calling-runtime
    Shared call state machine and call orchestration.
domain/calling-history
    Client call log, missed-call records, and related system messages.

domain/messaging/*
    Message sending, receiving, persistence hooks, and client behavior.

logic/api
    Shared experimental public contracts, configuration, and concrete result types.
logic/runtime
    Shared user runtime lifecycle and feature registration.
logic/client
    Full client composition.
logic/service
    Headless service composition.

logic
    Compatibility facade that keeps the existing client API available.
```

The exact names can change during implementation. The important part is keeping these boundaries.

## Module responsibilities

### `:logic:api`

This module contains the small public surface shared by client and service setups:

- Runtime lifecycle types.
- User, client, and conversation identifiers needed by public operations.
- Configuration types.
- Calling commands and events.
- Concrete success and failure types.
- Feature access contracts.

It must not depend on SQLDelight, Android WorkManager, client paging, or message database types.

### `:logic:runtime`

This module creates the shared runtime for one Wire identity. It owns:

- Coroutine scope and shutdown.
- Authenticated network access.
- WebSocket lifecycle.
- Crypto transaction access.
- Event handler registration.
- Common logging and metrics context.
- Access to installed features.

It must not decide whether messages or call history are stored.

### `:logic:client`

The client composition uses existing client defaults:

- Persisted accounts and tokens.
- SQLDelight user and global databases.
- Full conversation and user sync.
- Message sending, receiving, and persistence.
- Call history and missed-call system messages.
- Backup, client work scheduling, and other selected client features.

This module becomes the implementation behind the existing `:logic` facade.

### `:logic:service`

The service composition includes:

- Authenticated Wire network access.
- WebSocket event delivery, acknowledgement/checkpointing, decoding, and decryption.
- Calling-payload routing to AVS and encrypted outbound calling signalling through Wire APIs.
- Conversation data fetched on demand or supplied by the service.
- MLS/CoreCrypto operations.
- Calling runtime and calling notifications.
- In-memory active-call state by default.
- Hooks for service logging, metrics, and lifecycle events.

It excludes by default:

- Chat/application-message sending and message history. Encrypted calling signalling remains
  installed.
- Full conversation sync.
- Call history and missed-call messages.
- Paging and UI state.
- Android background work.
- Backup and other unrelated client features.

The service module should initially support the JVM target required by the PSTN service. Shared
code remains multiplatform when it is useful to clients and does not add service-only assumptions.

## Public API shape

The following is an example, not a final API contract:

```kotlin
val runtime = KaliumService.create(
    config = ServiceConfig(
        server = serverConfig,
        identity = serviceIdentity,
    ),
    credentialStore = credentialStore,
    cryptoStore = cryptoStore,
    eventCheckpointStore = checkpointStore,
    observers = ServiceObservers(
        calls = pstnCallObserver,
        metrics = metricsObserver,
    )
)

runtime.start()

val call = runtime.calls.join(
    conversationId = conversationId,
    media = mediaBridge
)

call.events.collect { event ->
    pstnRouter.handle(event)
}

call.leave()
runtime.close()
```

Important API rules:

- Creation does not start background work before `start()`.
- `close()` is explicit, idempotent, and cancels owned work.
- A failed feature does not silently start unrelated features.
- Public results are concrete types, not `Either`.
- Low-level AVS and CoreCrypto handles remain internal where possible.
- The runtime exposes only features that were installed by its composition.

## Conversation design

Calling does not need the full client conversation model. It needs a smaller operational view:

```kotlin
data class CallConversationContext(
    val conversationId: ConversationId,
    val type: ConversationType,
    val protocol: ConversationProtocol,
    val members: List<CallMember>,
    val clients: List<CallClient>,
    val mlsGroupId: GroupId?,
)
```

The actual type may differ, but it should contain only data needed to perform calling and MLS
operations.

The service implementation may fetch this context on demand and keep a short-lived cache. The
client implementation may build it from SQLDelight and existing repositories. Calling code should
not know which implementation was used.

Full sync, conversation lists, unread state, drafts, and message history stay outside
`conversation-runtime`.

## Event processing design

### Pipeline

```text
Wire WebSocket
    |
    v
Event source
    - reconnect
    - checkpoint/acknowledgement
    - raw envelope
    |
    v
Decode and decrypt
    |
    v
Event router
    |--------------------------|--------------------------|
    v                          v                          v
Calling handler       Conversation handler       Message handler
shared                shared minimum             client only
```

### Event handler contract

An event handler should declare which event types it accepts and return a result that tells the
router whether the event was handled, ignored, or failed.

```kotlin
interface EventHandler {
    fun accepts(event: Event): Boolean
    suspend fun handle(event: Event): EventHandlingResult
}
```

The router must define:

- Handler order when more than one handler accepts an event.
- Whether handlers run one after another or in parallel.
- Retry rules.
- When an event is acknowledged.
- What happens when an optional handler fails.
- How duplicate events are handled after reconnects.

For the first version, handlers should run in a clear, fixed order. An event should only be
acknowledged after all required handlers finish successfully.

The service composition does not register a message persistence handler. It can still handle call
signalling that arrives through the normal Wire event channel.

## Calling design

### Current concern

The current calling repository combines several jobs:

- Call configuration and SFT network requests.
- Active call state.
- Call database updates.
- Conversation and user lookup.
- MLS conference membership.
- Missed-call message creation.
- Recently ended call metadata.

This makes persistence part of the calling runtime even when a consumer does not need history.

### Proposed contracts

`calling-runtime` will depend on small contracts:

```kotlin
interface CallTransport {
    suspend fun getCallConfig(limit: Int?): CallConfigResult
    suspend fun connectToSft(url: String, payload: ByteArray): SftResult
    suspend fun sendSignal(signal: CallSignal): CallSignalResult
}

interface ConversationContextProvider {
    suspend fun getForCall(conversationId: ConversationId): ConversationContextResult
}

interface ConferenceMembership {
    suspend fun join(conversationId: ConversationId): ConferenceJoinResult
    suspend fun leave(conversationId: ConversationId): ConferenceLeaveResult
    fun observeEpochs(conversationId: ConversationId): Flow<EpochInfo>
}

interface CallStateStore {
    fun observeActiveCalls(): Flow<List<ActiveCall>>
    suspend fun update(call: ActiveCall)
    suspend fun remove(conversationId: ConversationId)
}

interface CallEventSink {
    suspend fun emit(event: CallLifecycleEvent)
}
```

The service default uses an in-memory `CallStateStore`. Its `CallEventSink` forwards call lifecycle
events to the PSTN service.

The client default can decorate the runtime store with call history. A separate client event sink
can create missed-call system messages. Neither behavior is required by `calling-runtime`.

### Call ownership

Each call must have one owning runtime instance. The runtime is responsible for:

- Joining and leaving the AVS call.
- Joining and leaving the MLS conference when required.
- Cancelling child coroutines.
- Closing native resources.
- Publishing the final call result.

The PSTN application owns telecom routing and decides when to request join or leave operations.

## Storage model

Storage is divided by purpose:

| State | Client default | Service default | Can be omitted? |
| --- | --- | --- | --- |
| Active call state | Memory with optional history | Memory | Yes after the call ends |
| Message history | SQLDelight | Not installed | Yes for service |
| Call history | SQLDelight | Not installed | Yes for service |
| Conversation catalog | SQLDelight | Remote lookup with bounded cache | Yes if it can be fetched again |
| Authentication refresh state | Existing local store | Service-supplied secure store | Usually no |
| Wire client identity | Existing local store | Service-supplied durable store | Usually no |
| CoreCrypto/MLS state | Existing crypto storage | Service-supplied durable storage | No for MLS correctness |
| Event checkpoint/idempotency | Existing event state | Service-supplied durable store | No for the initial service |

The SDK will not provide a single switch that disables every store. Each feature receives the store
it needs. A feature that is not installed does not need a no-op repository.

### Service isolation

If one service process handles several Wire identities, state must be isolated by at least:

- User ID.
- Client ID.
- Backend/domain.
- Runtime instance.

No mutable global cache may mix state between service identities. Provider cache policy must be set
to `LOCAL` for isolated runtime instances unless the service has a reviewed global cache design.

## Lifecycle and concurrency

Backend services may run for a long time and may handle several calls at once. The shared runtime
must define:

- Whether one runtime supports one or several active calls.
- Limits for concurrent calls and conversations.
- Startup and readiness states.
- Reconnect behavior.
- Graceful shutdown behavior.
- Timeouts for joining and leaving.
- Native resource cleanup.
- Backpressure for event and call streams.

The first PSTN version should use one runtime per Wire identity. If an identity can join several
calls, concurrency limits must be explicit in configuration. We should not depend on process-global
mutable state for call ownership.

## Errors and retries

The public service API should use clear result groups:

- Authentication failure.
- Conversation not found or access denied.
- Unsupported conversation protocol.
- MLS or crypto failure.
- Call configuration failure.
- SFT connection failure.
- Call rejected or closed.
- Event delivery failure.
- Runtime closed or not ready.

The network and event layers may retry transient failures. Domain operations must not retry actions
that are not safe to repeat unless they have an idempotency rule.

All retries need:

- A maximum attempt or time limit.
- Backoff with jitter.
- A metric and structured log.
- Cancellation when the runtime or call closes.

## Security

- Service credentials must not be logged.
- CoreCrypto and MLS state must use supported secure storage.
- Logs should use the existing obfuscated ID helpers.
- Service-supplied stores must define encryption, access, backup, and deletion rules.
- Runtime shutdown must clear sensitive in-memory references where supported.
- Test fixtures must not contain real credentials or production identifiers.
- PSTN metadata must not be added to Kalium logs unless it has a clear security review.

## Observability

The service composition needs first-class hooks for:

- Runtime started, ready, reconnecting, and closed states.
- Event receive, handle, retry, and failure counts.
- Call join time and result.
- SFT connection time and failure reason.
- Active call count.
- MLS join, leave, and epoch update failures.
- Resource cleanup failures.

Logs and metrics should carry safe correlation values for runtime, conversation, and call. They must
not expose access tokens, raw crypto payloads, or phone numbers.

## Compatibility and publishing

The current `:logic` artifact remains available during migration. It will eventually delegate to
`:logic:client`, keeping the existing client API stable where possible.

New public modules must use:

- Explicit API mode.
- ABI validation.
- Consumer-facing changelog entries.
- Clear stability markers for experimental APIs.

The service API can start as experimental while the PSTN integration proves the lifecycle and
storage contracts. Moving it to stable requires at least one production-like integration and a
review of restart and failure behavior.

## Test strategy

### Module tests

- Event routing with and without the message handler.
- Remote and local conversation context implementations against the same contract tests.
- Calling runtime with in-memory fakes for every dependency.
- Call history adapter tests separate from call state-machine tests.
- Runtime startup, cancellation, and idempotent shutdown tests.
- Duplicate and retried event tests.

### Composition tests

The build should enforce the expected dependency graphs:

- `:logic:service` must not depend on messaging, paging, Android WorkManager, or full client
  persistence.
- `:logic:client` must retain the existing required features.
- Both compositions must pass public API and ABI checks.

### Integration tests

- Authenticate and register or restore a Wire client.
- Start the WebSocket and recover after a disconnect.
- Resolve a conversation without full sync.
- Join and leave a Proteus call if it remains supported by the PSTN scope.
- Join and leave an MLS conference call.
- Restart the service and restore required crypto and client state.
- Receive call events without inserting message records.
- Close a runtime during an active call without leaking native resources.

## Migration plan

### Phase 0: Agree on contracts

Deliverables:

- Accept ADR 0010.
- Record provisional PSTN requirements and preserve existing supported call protocols.
- Require durable session, Wire client identity, CoreCrypto/MLS, checkpoint, and idempotency state.
- Record the provisional explicit lifecycle and one-runtime-per-identity ownership rules.
- Defer final contract confirmation to the calling-team review while keeping APIs experimental.

No production behavior changes in this phase.

### Phase 1: Extract event processing

Deliverables:

- Create storage-neutral event source and handler contracts.
- Route calling events through an installed calling handler.
- Keep the current client message handler and behavior unchanged.
- Add tests proving calling events can be handled without message persistence.

### Phase 2: Extract conversation runtime

Deliverables:

- Define the minimum conversation context needed by calling.
- Add local and remote implementations.
- Move calling away from the large client conversation repository.
- Add shared contract tests for both implementations.

### Phase 3: Extract calling runtime

Deliverables:

- Move call state and orchestration out of `UserSessionScope`.
- Introduce the calling contracts described above.
- Move missed-call and call-log behavior into `calling-history`.
- Keep existing client call APIs working through adapters.

### Phase 4: Add service composition

Deliverables:

- Add `:logic:api`, `:logic:runtime`, and `:logic:service`.
- Add explicit start and close lifecycle.
- Add service storage and observability hooks.
- Build a PSTN integration test or sample service.

### Phase 5: Move client composition

Deliverables:

- Add `:logic:client`.
- Move existing client wiring behind it.
- Keep the current `:logic` artifact as a compatibility facade.
- Compare client behavior and performance before and after the move.

### Phase 6: Harden and publish

Deliverables:

- Production-like restart, reconnect, and load tests.
- ABI validation and public API review for new artifacts.
- Service operations guide.
- Final decision on experimental or stable service API status.

For the pre-review implementation pass, "harden" means production compilation, static analysis,
module-graph inspection, ABI review, an operations guide, and a documented deferred-test matrix.
Restart, reconnect, integration, and load tests remain required before stabilization but are not
added or run until the calling team confirms the approach. The service API remains experimental.

## Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Too many small modules | Slow builds and hard navigation | Split only independently selectable capabilities and implementations |
| Client behavior changes during extraction | Regressions in existing apps | Keep compatibility adapters and run current client tests in every phase |
| Service loses required MLS state | Calls fail after restart | Define durable crypto storage before production use and add restart tests |
| Event is acknowledged before required work finishes | Lost call state | Make acknowledgement part of the event-processing contract |
| Event is processed more than once | Duplicate call actions | Add event identity and idempotency rules |
| Global caches mix service identities | Security and correctness issues | Use local provider caches and key every shared resource by identity |
| Call runtime still depends on client models | Service remains coupled to `:logic` | Add narrow conversation and call models with architecture tests |
| Compatibility facade becomes permanent duplicate wiring | Ongoing maintenance cost | Make it delegate to `:logic:client` and track removal of old wiring |
| Service API is stabilized too early | Hard-to-change lifecycle API | Publish it as experimental until the PSTN flow is proven |

## Alternatives considered

### Keep one `:logic` module and add `serviceMode`

Rejected. This keeps client dependencies in service builds and spreads mode checks through shared
code. It also makes unsupported combinations possible.

### Add no-op message and persistence implementations

Rejected as the main design. A no-op can hide an accidental dependency and can lose state that is
required for protocol correctness. Features that are not needed should not be installed.

### Fork Kalium for the PSTN service

Rejected. Calling, MLS, and Wire APIs would drift between the client and service versions. Fixes
would need to be copied between forks.

### Build a PSTN-only public module

Rejected for the shared SDK layer. The useful boundary is a generic headless service runtime.
PSTN-specific routing belongs above Kalium.

### Rewrite the SDK around a new dependency injection framework

Rejected. Constructor injection is enough. A new framework would increase the scope and require a
separate architecture decision.

## Open questions

The following questions remain for calling-team and deployment review. The pre-review implementation
uses the safe defaults above and keeps affected APIs experimental:

1. Which call types must the first PSTN version support: MLS conference only, or Proteus as well?
2. Will one service identity handle one active call or several concurrent calls?
3. Who creates and owns the Wire service user and client registration?
4. Where will auth tokens, client identity, and CoreCrypto state be stored?
5. What event delivery guarantee does the service require after a restart?
6. Can conversation context always be fetched on demand, or is a small durable cache required?
7. Does the service eventually need chat/application messages in addition to the required calling
   signalling? The initial composition installs only calling signalling.
8. Which metrics and traces are required by the PSTN operations team?
9. Is `:logic:service` published as a normal Maven artifact or consumed from a service-specific
   release line at first?
10. What load and call concurrency must one runtime and one process support?

## Decision request

Reviewers are asked to agree on these points:

1. Kalium will be split by capability rather than by product.
2. Client and service behavior will be selected in top-level composition modules.
3. Message persistence and call history will not be required by the calling runtime.
4. Event delivery will be separate from message persistence.
5. Required session, Wire client identity, CoreCrypto/MLS, event checkpoint, and idempotency state
   will remain durable and will be supplied by the service.
6. The existing `:logic` API will remain compatible during migration.
7. PSTN routing will stay outside the shared Kalium runtime.

## Success measures

The proposal is successful when:

- A JVM service can join and leave a Wire call through Kalium.
- The service artifact has no dependency on message persistence, paging, or Android work modules.
- Calling events are processed without creating message records.
- The service can restart and restore the state required to join the next call safely.
- Existing client tests and public APIs remain green during migration.
- Calling runtime tests do not require `UserSessionScope` or SQLDelight.
- Client and service module graphs are checked automatically.
