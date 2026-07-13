# 10. Lightweight Kalium for an iOS Notification Service Extension

Date: 2026-07-13

## Status

Accepted for spike; production acceptance depends on the feasibility gates in this ADR.

## Context

The new iOS application will not embed the full `KaliumLogic` SDK. Its Notification Service
Extension (NSE) still needs a small Kalium-based component that can use an authenticated
notification WebSocket, catch up incrementally, decrypt incoming Proteus and MLS messages, and
produce notification content. The NSE must share synchronization and cryptographic state with the
foreground application without running the two synchronization pipelines concurrently.

An NSE has a short, externally controlled lifetime. It must finish through the notification content
handler even when networking, storage, or decryption cannot complete. The existing incremental-sync
pipeline is intentionally long-lived and therefore cannot be reused as an NSE entry point:

- `logic/build.gradle.kts` builds the `KaliumLogic` XCFramework and links the broad application
  dependency graph, including full calling, backup, cells, persistence, networking, sending, and
  other domain features. The NSE requires a separately assembled framework rather than conditional
  runtime flags inside this artifact.
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/IncrementalSyncManager.kt`
  exposes a flow that does not end and automatically retries failures.
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/EventGatherer.kt` buffers the
  WebSocket flow without a bounded capacity and starts observing stored events after the catch-up
  boundary.
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/IncrementalSyncWorker.kt`
  processes complete batches in `NonCancellable`, combines CoreCrypto work with full application
  event handling, and marks the buffered events processed afterwards.
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/data/event/EventRepository.kt` currently owns
  transport, raw-event persistence, event mapping, cursor management, ACKs, and slow-sync recovery.
  For a consumable notification it inserts into the `Events` table, attempts to enqueue the
  delivery-tag ACK, and updates `last_processed_event_id` in separate operations. These operations
  are ordered but are not one atomic database transaction. Cursor update failures are not propagated
  from that callback. The current API returns after a local WebSocket `trySend`; it does not confirm
  that the backend received the ACK
  (`data/network/src/commonMain/kotlin/com/wire/kalium/network/api/v9/authenticated/NotificationApiV9.kt`).
- `data/persistence/src/commonMain/db_user/com/wire/kalium/persistence/Events.sq` gives raw events a
  unique server event ID and buffers them independently from the main message tables. This is a
  useful durability pattern, but the existing table is part of the full user database and is not a
  cross-process handoff contract.
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/EventProcessor.kt` and
  `IncrementalSyncWorker.kt` process a batch inside a combined Proteus/MLS transaction, then mark
  event rows processed only after the CoreCrypto transaction succeeds. Full event handling also
  writes the application database and schedules side effects. CoreCrypto changes, application
  database writes, and the later processed-state update are not one atomic transaction;
  `dbInvalidationController.runMuted` suppresses invalidations rather than opening such a
  transaction.

The existing message receivers also combine operations needed for receiving with operations that
must not run in the NSE:

- `logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/MLSMessageUnpacker.kt`
  decrypts MLS messages but also schedules commits for pending proposals.
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/NewMessageEventHandler.kt`
  persists full application messages, sends delivery confirmations, schedules self-deletion, and
  performs stale-epoch or reset recovery.
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSWelcomeEventHandler.kt`
  can fetch conversation data, fetch certificate revocation lists, join through an external commit,
  and refill key packages.
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/data/message/ProtoContentMapper.kt` combines
  protobuf decoding with mappings for the full application model and sending path.

Notification decisions currently depend on more than decrypted content. For example,
`data/persistence/src/commonMain/db_user/com/wire/kalium/persistence/Notification.sq` joins message,
conversation, sender, legal-hold, archive, mute, visibility, unread, and last-notified data. A
lightweight receiver cannot reproduce the current notification policy from a protobuf blob alone.

The repository already contains a notification-only AVS boundary. The
`:domain:calling-notifications` module uses `wcall_event_create`, `wcall_event_start`,
`wcall_event_process`, and `wcall_event_end` on Apple in
`domain/calling-notifications/src/appleAvsMain/kotlin/com/wire/kalium/calling/notifications/AppleAvsCallNotificationProcessor.kt`.
The NSE should use this module and must not link the full `:domain:calling` stack.

Apple storage is not ready to be shared securely between the application and extension without
additional work:

- `data/persistence/src/appleMain/kotlin/com/wire/kalium/persistence/db/PlatformDatabaseData.kt`
  uses the native SQLite driver and contains a TODO for SQLCipher encryption.
- `logic/src/appleMain/kotlin/com/wire/kalium/logic/CoreLogic.kt` and
  `domain/userstorage/src/appleMain/kotlin/com/wire/kalium/userstorage/di/PlatformUserStorageProvider.kt`
  pass no database passphrase on Apple.
- `data/persistence/src/appleMain/kotlin/com/wire/kalium/persistence/kmmSettings/ApplePersistenceConfig.kt`
  currently exposes only a Keychain service name. The current `KeychainSettings` construction does
  not configure a shared Keychain access group or an NSE-compatible accessibility class.
- Apple storage and CoreCrypto locations are derived from the caller-provided root path. The host
  must deliberately use an App Group container if the app and extension are to see the same files.

This ADR defines the target architecture and the contracts that the spike must validate. It does
not assert that SQLite, Keychain, CoreCrypto, AVS, or the notification WebSocket already work under
all NSE constraints.

## Decision

### Scope of the spike

The spike will establish whether a lightweight Apple framework can:

- initialize from an NSE and use shared account, client, authentication, and cryptographic state;
- acquire a per-account cross-process synchronization lock;
- perform one bounded notification catch-up over the notification APIs and WebSocket;
- durably stage remote events before transport acknowledgement;
- apply the passive cryptographic state changes required to receive Proteus and MLS messages;
- retain the exact decrypted protobuf blobs and extract only the information required for a local
  notification;
- process call-event payloads through `:domain:calling-notifications`;
- hand staged events to the foreground application's native persistence layer; and
- exit through a safe notification fallback before the NSE deadline.

The spike may use deliberately narrow or temporary APIs while feasibility is being measured. It
must preserve the boundaries in this ADR so that spike-only shortcuts do not become dependencies of
the production design.

### Non-goals

The NSE component will not:

- expose or embed the full `:logic` SDK;
- run slow sync or conversation-history sync;
- send user-authored messages;
- send chat delivery confirmations or read receipts;
- send MLS commits, external commits, reset messages, key-package refills, or other recovery traffic;
- schedule delayed pending-proposal commits;
- fetch missing application metadata as part of normal notification rendering;
- own the application's main message database;
- import decrypted rows into the main database while running in the NSE;
- provide continuous WebSocket synchronization after the initial catch-up boundary;
- link full AVS or the full `:domain:calling` module; or
- promise exactly-once visible notification delivery, which cannot be made atomic with iOS
  presentation. It will instead provide stable notification identifiers and idempotent processing.

The initial implementation is an explicitly time-boxed spike. Automated tests are deferred until
the platform feasibility has been verified. Manual evidence and measurements are nevertheless
required before the spike is accepted.

### Layer and module boundaries

New code will preserve the dependency direction established by ADR 4:

```text
:core:*  <-  :data:*  <-  :domain:*  <-  :logic:*
```

The target module responsibilities are:

| Module | Responsibility |
| --- | --- |
| `:core:data` | Small platform-neutral identifiers, encrypted/decrypted envelope results, and stable values that are useful across data and domain modules. It must not contain SQLDelight or NSE orchestration. |
| `:data:network-model` | Existing wire notification DTOs and transport payloads. |
| `:data:network` | Existing notification HTTP/WebSocket implementation, exposed to the bounded sync through a narrow notification transport facade rather than the complete authenticated API surface. |
| `:data:protobuf` | Generated protobuf types and primitive binary serialization only. |
| `:data:message-content` | New narrow protobuf-to-shared-content codec, depending only on `:core:data` and `:data:protobuf`. This avoids pulling the existing broad `:data:data-mappers` dependency graph into the NSE. |
| `:data:data-mappers` | Full-application persistence/network mappings. It may consume the narrow codec but remains outside the NSE graph unless its dependencies are independently reduced. |
| `:data:notification-inbox` | New versioned SQLDelight handoff database, cursor journal, notification-policy snapshot storage, and data access contract. It must not depend on domain or logic modules. |
| `:data:sync-coordination` | New platform infrastructure for a stable per-account process lock. Its Apple implementation will own the POSIX file descriptor and non-blocking `flock` operation. |
| `:domain:messaging:receiving` | Reusable Proteus/MLS receive-only decryption, external-content resolution, protobuf decoding orchestration, and receive failure classification. The existing empty module is the extraction target. |
| `:domain:notification-sync` | New bounded catch-up state machine. It composes notification transport, the inbox, the cursor journal, the process lock, and receive-only decryption without application lifecycle or infinite retries. |
| `:domain:calling-notifications` | Existing notification-only AVS processor. It is consumed directly without `:domain:calling`. |
| `:logic:notification-extension` | New Swift-facing assembly module/XCFramework for the NSE. It selects an account, supplies an absolute deadline, maps concrete results for Swift, invokes the iOS completion callback, and wires the lower layers. It must not expose `Either`. |
| `:logic` | The full Kalium application assembly. It will be migrated to consume the same receiving and bounded-sync primitives where applicable, while retaining its continuous manager, main-database projectors, sending, and recovery behavior. |

The names of new modules may be adjusted during implementation, but their responsibilities and
dependency direction are part of this decision. In particular, the current `EventRepository`,
`EventMapper`, `IncrementalSyncManager`, `IncrementalSyncWorker`, and `ProtoContentMapper` will not be
moved wholesale into the NSE dependency graph.

The native foreground application will not depend on a Kalium framework. The handoff schema, lock
path derivation, state values, and transaction rules are therefore a versioned cross-language API
that must also have a native Swift implementation. Generated SQLDelight entity classes are not part
of that API.

### One bounded synchronization run

The reusable operation will be conceptually equivalent to:

```kotlin
suspend fun syncOnce(accountId: AccountId, deadline: Instant): SyncOnceResult
```

It has a finite state machine:

```text
ACQUIRE_LOCK
    -> PREPARE
    -> CONNECT
    -> CATCH_UP_AND_STAGE
    -> DRAIN_STAGED_EVENTS
    -> EVALUATE_NOTIFICATIONS
    -> COMPLETE
    -> RELEASE_LOCK
```

Every state checks a caller-supplied deadline and preserves a safety margin for closing resources
and invoking the NSE content handler. The state machine does not own an infinite retry loop. It may
perform only a small, bounded retry when the remaining budget permits and the result identifies the
attempt as safe. The result distinguishes at least complete, partial/retryable, lock unavailable,
deadline reached, foreground recovery required, and terminal configuration failure.

`NonCancellable` must not enclose a WebSocket collection, a full event batch, or notification
processing. Cancellation may be delayed only for a small storage or CoreCrypto transaction whose
worst-case duration is measured during the feasibility spike.

For consumable notifications, the current session marker remains the catch-up boundary. Events
received before the matching marker are staged as catch-up events. The matching marker transitions
the run to draining; this run then closes its WebSocket instead of becoming a continuous live
listener. Delivery tags are acknowledged according to the durability rule below.

For a legacy backend, the WebSocket is opened before pending pages are fetched, preserving the
current protection against the gap between HTTP pagination and live delivery. Pending pages are
read from the shared cursor and staged idempotently. The exact legacy cutoff/drain behavior and race
between the final page and buffered WebSocket messages must be proven in Milestone 1 before it is
treated as stable.

### Durability before transport acknowledgement

A transport ACK means that this client has durably accepted responsibility for a server event. It
does not mean the user has read the message or that the main application database has imported it.

For each remote event:

1. Derive the stable account-scoped event key.
2. In one handoff-database transaction, insert the raw event idempotently and advance the shared
   non-transient cursor to that durably staged event.
3. Commit the transaction.
4. Only then attempt to enqueue the WebSocket delivery-tag ACK, when one exists.
5. If the open WebSocket cannot accept the ACK, return a retryable/partial result. A redelivery finds
   the existing raw row, verifies it, and repeats the ACK without duplicating application work.

Transient events are staged when they are needed for foreground or notification behavior but do not
advance the durable server cursor. A synchronization marker is acknowledged only after it is
validated for the current run. The marker does not advance the event cursor.

The cursor and event row must never be committed independently. This intentionally strengthens the
current `EventDataSource` behavior, where the event insert and metadata update are separate calls.
The existing crash gap is normally recovered by cursor-based redelivery plus `INSERT OR IGNORE`, but
the new shared journal should not rely on that gap.

The narrow notification transport must report whether the ACK was accepted by the active local
WebSocket writer. If the wire protocol has no ACK-of-ACK, this result is not described as backend
confirmation. A process death or connection loss after local enqueue remains recoverable through
backend redelivery and the idempotent event row. Delivery tags and markers are scoped to a WebSocket
session; they are not persisted for replay through a future session.

Transport acknowledgement remains in NSE scope because the consumable notification protocol needs
it. Chat delivery confirmations, read receipts, confirmation messages, and any other user-visible
receipt are sending features and remain out of NSE scope. The two kinds of acknowledgement must use
different names and interfaces so they cannot be confused.

### Cursor ownership

There is one durable notification cursor per account/client pair. It is owned by the shared handoff
store, not by process memory, the full Kalium `Metadata` table, or the native application's main
message database. Both the foreground synchronizer and NSE synchronizer must read and update this
same cursor under the same per-account process lock.

Cursor advancement records that the raw event is recoverable from the handoff store. It does not
wait for decryption, notification presentation, or foreground import. Those later stages are
idempotent and independently resumable.

During migration, the existing `last_processed_event_id` value can seed the shared cursor exactly
once while the foreground process owns the lock. After cutover there must not be two authoritative
cursors. Rollback and downgrade behavior for this cutover is an open release decision.

### Handoff event model and lifecycle

The handoff database is a durable cross-process inbox, not merely a notification cache. Each server
event has one parent raw-event record. It can have zero or more child receive results because one
notification payload can contain multiple event contents and MLS decryption can release buffered
application messages.

The parent record retains, at minimum:

- schema and payload-format versions;
- account/client and stable server event IDs;
- raw event envelope bytes or a lossless serialized representation;
- server timestamp, transient flag, delivery source, and received timestamp;
- cursor position associated with the commit;
- whether foreground-only recovery is required; and
- a content hash used to detect an impossible same-ID/different-payload collision.

Each child receive result retains, at minimum:

- a deterministic idempotency key, preferably the protocol message UID when present and otherwise a
  stable event-key plus item index;
- conversation, sender, client, protocol, and message timestamps needed by the importer;
- the exact decrypted `GenericMessage` protobuf blob when an application message exists;
- whether the CoreCrypto state change was already applied;
- a receive classification such as application message, handshake-only, welcome, unsupported, or
  non-message event; and
- failure classification and a bounded retry count without logging payload contents.

A single status enum cannot safely represent independent work by the NSE and foreground app. The
database will maintain independent state dimensions:

| Dimension | States |
| --- | --- |
| Ingestion | `RAW_STORED`, `CORRUPT` |
| Decryption | `NOT_REQUIRED`, `PENDING`, `DECRYPTED`, `HANDSHAKE_APPLIED`, `DEFERRED_TO_APP`, `FAILED_RETRYABLE`, `FAILED_TERMINAL` |
| Notification | `NOT_ELIGIBLE`, `PENDING`, `PRESENTED`, `SUPPRESSED`, `FAILED` |
| Foreground import | `PENDING`, `IMPORTED` |

State transitions are transactional and monotonic except for an explicitly bounded retry transition.
Claim/lease timestamps must allow a process killed during `PENDING` work to be recovered without
leaving rows permanently owned. Notification presentation uses the child idempotency key as the
platform notification identifier where possible, so replay replaces or updates an existing request
instead of creating an unrelated duplicate.

Raw non-message events remain pending for foreground import unless the cross-language contract
explicitly classifies them as safe to discard. Unknown future event and protobuf types are retained
losslessly and deferred; they must not crash the NSE or be acknowledged without durable raw storage.

Imported rows are not deleted in the import transaction. Cleanup is a later bounded operation with
an agreed retention window so crashes, diagnostics, and downgrade scenarios remain recoverable.

### Cross-process coordination

The app and NSE use a stable per-account lock file inside their shared App Group container. The file
name is derived from a non-secret stable account/client identifier, preferably a digest. The lock is
an advisory exclusive POSIX `flock` held by keeping the file descriptor open. File existence is not
lock ownership, and the lock file is not deleted to release it. The kernel releases the lock if a
process exits.

The NSE always requests `LOCK_EX | LOCK_NB`. If the lock is unavailable it does not wait or poll; it
immediately uses the fallback path. The foreground application may wait for a short, explicitly
bounded interval appropriate to its lifecycle, but it must not start synchronization or mutate the
shared cursor while another owner holds the lock.

Until CoreCrypto multi-process behavior is proven safe, the lock covers the entire NSE operation,
including:

- authentication token reads and any refresh/write;
- first-use passphrase lookup/generation and persistence;
- cursor reads and updates;
- WebSocket catch-up and transport ACKs;
- handoff-store writes and state transitions;
- CoreCrypto initialization, all Proteus/MLS transactions, and close; and
- notification-only AVS processing that consumes the synchronized batch.

The foreground process must use the same lock for its notification synchronization and for every
CoreCrypto mutation that could overlap an NSE invocation, including message sending and MLS group
administration. An in-process Kotlin `Mutex`, limited-parallelism dispatcher, provider cache, or
SQLite WAL is not a cross-process substitute. If Milestone 1 proves a narrower CoreCrypto lock scope
is supported, narrowing it requires an update to this ADR.

No access token refresh may run concurrently in the app and NSE. The serial dispatcher used by
`logic/src/commonMain/kotlin/com/wire/kalium/logic/network/SessionManagerImpl.kt` is process-local;
the shared process lock is the cross-process authority. The provider mutexes around CoreCrypto are
also process-local. First-use passphrase creation is a read/generate/write sequence, so it must occur
under the same process lock to prevent two processes from creating different keys for one store.

### Receive-only cryptography

“Passive” MLS receiving still mutates the shared CoreCrypto database. It includes applying an
already-received MLS application message, commit, or proposal through `decryptMessage`, because
handshake processing is required to advance the local epoch and decrypt following messages.
Handshake-only results are recorded even when they contain no application protobuf.

The reusable receiving module may perform only cryptographic work caused by already-received bytes:

- Proteus decryption and session-state advancement;
- MLS message decryption and inbound handshake-state advancement;
- release of buffered MLS application messages;
- external-content decryption; and
- pure protobuf decoding and notification-field extraction.

It may accept an already-received MLS Welcome only if the spike proves a bounded, local-only path
that does not fetch metadata, send an external commit, refill key packages, fetch CRLs, or schedule
outgoing work. Otherwise the Welcome and dependent messages are stored as `DEFERRED_TO_APP`. A
proposal `commitDelay` is also persisted as foreground work and is not scheduled by the NSE.

The NSE excludes the active recovery branches currently present in `NewMessageEventHandler` and
`MLSWelcomeEventHandler`: stale-epoch verification, conversation reset, external-commit recovery,
key-package refill, delayed commit, CRL network fetch, and user-visible recovery messages. It records
enough failure and raw input information for the foreground application to recover later.

CoreCrypto, Proteus, and MLS database paths must resolve into the App Group container, and their
passphrases must be available through the shared Keychain access group. The app and extension must
never maintain divergent copies of cryptographic state for the same account/client.

### Notification policy and notification-only AVS

Decrypted protobuf alone is insufficient for the existing notification policy. Before backgrounding,
the foreground app will publish a versioned, minimal notification-policy snapshot into shared
storage. It can include conversation mute/archive/type, last-notified watermark, sender display
data, self-user/client IDs, content-privacy preference, and any legal-hold or membership state that
the approved policy needs. The snapshot must contain no more profile or conversation data than the
NSE requires.

The NSE's notification extractor is a pure component. Given decrypted protobuf, stable envelope
metadata, and the policy snapshot, it returns a structured candidate or a reason to suppress/defer.
It does not write the main database or call iOS APIs. Missing or stale policy data results in the
approved privacy-preserving generic notification, not an optimistic disclosure of message text.

Calling candidates are converted to `AvsCallNotification` and passed only to
`AvsCallNotificationProcessorFactory` from `:domain:calling-notifications`. The processor is created
late, processes a bounded batch, and is closed before the NSE completion handler. The lightweight
framework must be dependency-audited to ensure full AVS/calling code is not linked. The Kotlin
module boundary alone does not prove a smaller or NSE-safe native binary: both calling modules use
the Apple `avs-kmp` artifact. Native symbols, initialization behavior, memory, and linked-size deltas
are Milestone 1 gates. The spike also verifies that processor `close()` is called exactly once and
that its native lifecycle does not require an additional destroy operation.

### Foreground import and crash recovery

When the application enters the foreground, its native importer:

1. acquires the same account lock;
2. stops or excludes any competing foreground synchronization for that account;
3. reads handoff children whose import state is `PENDING` in deterministic server/event order;
4. imports exact decrypted protobuf messages and required raw non-message events into the main
   database using stable upsert/idempotency keys;
5. commits the main-database transaction;
6. only after that commit, marks the corresponding handoff records `IMPORTED`; and
7. starts or resumes foreground synchronization from the same shared cursor.

The main database and handoff database cannot share one transaction. A crash after step 5 but before
step 6 therefore replays the import. Main-database upserts must make that replay harmless. Marking a
handoff row imported before the main commit is forbidden because it can lose the only materialized
copy.

Messages already decrypted by the NSE are not decrypted a second time. In particular, an MLS
handshake already applied to shared CoreCrypto state must not be replayed. The `crypto state applied`
flag and receive classification tell the importer whether it should materialize application data,
schedule deferred foreground work, or simply record completion.

Foreground synchronization can receive an event already staged by the NSE. It must use the same
event and child idempotency keys, merge with the existing row, and continue import rather than create
a second message. App import completion does not control the server cursor; durable raw staging does.

### Apple storage and entitlement requirements

The host app and NSE must have matching App Group and Keychain Sharing entitlements. The host passes
the resolved App Group container URL into the lightweight framework; neither Kalium nor the NSE may
derive a shared path from `NSHomeDirectory()`.

All shared files are located below a versioned App Group subtree with per-account isolation:

```text
<app-group-container>/kalium-nse/v1/<account-digest>/
    sync.lock
    handoff/
    corecrypto/
```

Actual CoreCrypto subpaths must preserve the layout expected by the existing crypto providers. No
account ID, conversation ID, sender ID, or message text appears in a file name.

The handoff database and CoreCrypto databases must be encrypted at rest with independent keys stored
in the shared Keychain access group. Apple database encryption is a feasibility gate because the
current native SQLDelight driver does not enable SQLCipher. Milestone 1 will choose between a
supported SQLCipher driver and a reviewed design that encrypts sensitive blobs while still
protecting cursor/schema metadata. Plaintext decrypted protobuf in an unencrypted SQLite file is not
acceptable for production.

The Keychain configuration must be extended beyond `serviceName` to include a shared access group
and an accessibility class that is available to the NSE under the supported device-lock policy.
Keys should be device-only and non-synchronizable unless product security explicitly approves a
different recovery requirement. Shared database files use an iOS file-protection class compatible
with the same background policy. Locked-device and first-unlock behavior are measured explicitly.

SQLite journal, WAL, and shared-memory files remain within the protected App Group directory. Schema
migrations run only while the account process lock is held. The schema has an explicit compatibility
version so an older app or extension fails closed rather than interpreting newer rows incorrectly.
Cross-process consumers explicitly query the handoff store when they acquire the lock; they do not
rely on SQLDelight Flow invalidations, which are driver/process-local.

### Failure and fallback behavior

The NSE always preserves enough time to invoke its completion handler. Fallback content must be
privacy-preserving and deterministic. Specific behavior is:

- **Lock unavailable:** do not open storage, refresh authentication, connect, or initialize
  CoreCrypto. Return `LockUnavailable` and use the approved generic/original push fallback.
- **Deadline approaching:** stop accepting new work, finish only the current bounded atomic
  operation, close resources, and complete with the best candidate already produced or a generic
  fallback.
- **Authentication or network failure:** retain any already staged rows, do not advance the cursor
  for unstaged events, and return retryable/partial. Do not log tokens or payloads.
- **Raw storage failure:** do not transport-ACK and do not advance the cursor.
- **Transport ACK enqueue failure after durable storage:** keep the row, return retryable/partial,
  and ACK an idempotent redelivery later. If enqueue succeeded but backend receipt is unknowable,
  rely on the same redelivery rule rather than claiming confirmed delivery.
- **Decryption failure:** keep the raw row and classify it as retryable, terminal, or deferred to the
  foreground. Cursor and transport ACK remain valid because the raw input is durable.
- **Missed-notification signal:** persist `foreground recovery required`, do not run slow sync, and
  do not send the full missed-event acknowledgement. Only the foreground recovery path may perform
  slow sync and then acknowledge that condition.
- **Unsupported event/protobuf:** preserve it losslessly for foreground import and avoid exposing
  unknown content in a notification.
- **Missing/stale policy snapshot:** suppress content details and use a generic notification.
- **Notification AVS failure:** close the notification processor, record a non-sensitive failure,
  and use the approved generic call fallback.
- **Handoff corruption or incompatible schema:** fail closed without deleting or recreating shared
  state from the NSE. The foreground app owns repair/recovery UX.

Logging and metrics contain operation, duration, count, coarse failure classification, and hashed
account correlation only. They do not contain decrypted protobuf, raw event payload, access/refresh
tokens, message text, user handles, conversation names, or unobfuscated IDs.

### Security and privacy properties

The remote push is a trigger, not trusted message content. Notification content comes only from an
authenticated notification stream and successfully decrypted bytes. Certificate pinning and server
configuration remain consistent with the foreground account.

The design minimizes plaintext lifetime: decrypted bytes are held only for decode/extraction and the
encrypted handoff write, sensitive buffers are not copied into logs, and imported rows are cleaned
after the retention window. The notification policy snapshot follows data minimization and is
versioned separately from message data.

The per-account lock is both a synchronization and a security boundary. Every path is validated to
remain inside the resolved App Group directory; account-derived path components are digests rather
than raw input. Symlink/path traversal and same-event-ID/different-payload collisions fail closed.

Keychain access-group, App Group, file-protection, database-encryption, CoreCrypto concurrency, and
locked-device behaviors require review by the iOS and security owners before production rollout.

### Staged migration

Implementation proceeds in stages that keep the current full Kalium path working:

1. **Architecture and feasibility:** accept this ADR, then prove the Apple assumptions without
   changing current sync behavior.
2. **Receive-only extraction:** move pure protobuf decoding and Proteus/MLS receive operations into
   `:domain:messaging:receiving`; full `:logic` consumes them first.
3. **Bounded-sync extraction:** introduce the narrow notification transport, inbox contract, cursor
   journal, and finite state machine. Adapt the existing full manager around the bounded primitive.
4. **Cross-process storage and lock:** add the handoff schema, Apple encryption/key configuration,
   process lock, and Swift interop contract.
5. **NSE assembly:** create the lightweight framework with notification-only AVS and dependency-size
   auditing.
6. **Foreground importer:** integrate the native main database and shared cursor protocol.
7. **Hardening and rollout:** add automated coverage after spike verification, cancellation/crash
   fault injection, security review, performance budgets, observability, feature flags, and rollback.

No stage switches the authoritative cursor until both app and NSE implementations understand the
shared schema and lock. Feature flags must support disabling NSE sync without destroying staged data.

### Milestone 1 feasibility measurements and exit criteria

Milestone 1 is a disposable iOS prototype, not production implementation. It must run on a physical
supported iPhone; simulator evidence is supplemental. Record device model, iOS version, build mode,
backend/API version, network conditions, backlog size, and whether the device has been unlocked
since boot.

Measure at minimum:

- lightweight framework and containing app binary-size deltas, including linked native libraries;
- NSE cold-start time from entry to the first useful instruction;
- peak resident memory for initialization, WebSocket catch-up, CoreCrypto, protobuf decode, and AVS;
- App Group database open/migration/read/write latency from both processes;
- non-blocking lock acquisition latency, contention result latency, and kernel release after forced
  process termination;
- shared Keychain read/write latency and behavior when locked, after first unlock, and after app/NSE
  reinstall combinations permitted by iOS;
- WebSocket connect-to-open, catch-up-to-marker, raw-stage, and transport-ACK latency for empty,
  small, and representative backlog sizes;
- Proteus application-message decryption latency;
- MLS application, handshake-only, buffered-message, and Welcome/deferred behavior and transaction
  latency;
- notification candidate extraction and notification-only AVS create/process/close latency;
- foreground handoff read and idempotent import latency; and
- remaining deadline margin on successful, contended, offline, crypto-failure, and forced-expiry
  paths.

Milestone 1 passes only when all of the following are demonstrated:

- the NSE loads the lightweight KMP framework on a physical device and always calls its completion
  path in the tested failure scenarios;
- app and NSE resolve the same App Group paths and shared Keychain items with approved entitlements
  and protection classes;
- plaintext decrypted protobuf cannot be recovered from the handoff database and its journal files;
- `flock` prevents simultaneous ownership, NSE contention returns immediately, and a killed owner
  leaves no permanent lock;
- app and NSE can open the same CoreCrypto state sequentially under the lock, and Proteus plus MLS
  receive operations preserve state across process handoff without corruption;
- no NSE code path sends a chat receipt, message, MLS commit, key package, recovery request, or slow
  sync request;
- the consumable WebSocket reaches its marker, persists before ACK, and safely handles a redelivery;
- notification-only AVS works without linking or initializing full AVS/calling;
- a row staged by the NSE is imported idempotently by the foreground prototype, including a forced
  crash between main-database commit and `IMPORTED` marking;
- memory, size, latency, and deadline-margin results are documented and approved against explicit
  budgets set by the iOS owners after the first measurement pass; and
- every failed feasibility gate has a recorded decision to fix, redesign, or stop before production
  extraction begins.

### Spike sequencing amendment — 2026-07-13

The host and simulator spike produced enough evidence to proceed with the receive-only extraction
in Milestone 2 without claiming production NSE feasibility. This amendment permits Milestone 2 to
use iOS Simulator compile, link, framework-load, and manual-probe evidence while the signed
physical-device gates remain open.

This does not waive the physical-device, App Group, Keychain, encrypted handoff storage,
locked-device, APNs/backend, memory, deadline, real Proteus/MLS receive, or extension-validation
gates. Those gates must pass before the NSE framework is considered production-ready or any
authoritative cursor/state cutover occurs. The split CoreCrypto and AVS framework arrangement is
provisional and must be repeated inside a signed physical-device NSE.

## Consequences

### Benefits

- The NSE has a small, finite API instead of depending on application lifecycle and an endless sync
  manager.
- A transport ACK is never enqueued before the client has a durable recovery copy.
- One shared cursor and one process lock make app/NSE ownership explicit.
- Receive-only cryptography can be reused by full Kalium without importing sending and recovery into
  the extension.
- Exact protobuf retention keeps the native importer forward-compatible and avoids lossy
  notification-specific persistence.
- Independent ingestion, decryption, notification, and import states make process termination
  recoverable.
- Notification-only AVS already has a focused boundary and can be dependency-audited separately.

### Costs and trade-offs

- A new encrypted cross-language database and its migrations become a compatibility surface shared
  by Kotlin and Swift.
- There is no atomic transaction across the handoff and native main databases, so idempotent replay
  is mandatory.
- Locking the whole NSE run favors correctness over concurrency and can cause generic notifications
  while the app owns the account. Measurement may justify a narrower scope later.
- Cursor cutover and downgrade require release coordination because two cursor authorities would
  risk gaps or replays.
- A notification-policy snapshot duplicates a deliberately small amount of application metadata.
- MLS receive-only extraction is not just pure decryption: inbound handshake processing mutates
  shared crypto state and must be coordinated with all foreground crypto operations.
- Apple database encryption, Keychain access groups, and CoreCrypto multi-process use are unresolved
  feasibility gates rather than existing Kalium capabilities.

### Risks

- iOS may terminate the NSE during a native or database operation that cannot be bounded tightly
  enough.
- The native CoreCrypto/SQLite implementation may not support sequential opens from separate
  processes in the required lifecycle even with an external lock.
- The foreground app may use CoreCrypto outside the shared lock and reintroduce corruption or epoch
  races.
- A stale notification-policy snapshot can suppress a desired notification; an unsafe fallback can
  leak message content. The decision is to prefer privacy-preserving suppression/generic content.
- Processing an MLS handshake without the corresponding application metadata may leave the app and
  crypto projections temporarily inconsistent.
- Legacy pending-event and WebSocket cutoff semantics may be difficult to make finite without a
  server-supported marker.
- Notification presentation cannot be committed atomically with `PRESENTED`, so duplicates remain
  possible across a process kill despite stable identifiers.
- An older foreground app may not understand a newer handoff schema or receive classification.

### Open decisions

The following decisions must be closed by the named milestone before production work depends on
them:

- **Milestone 1:** whether SQLCipher is supported for the Apple SQLDelight driver or sensitive blobs
  require an independently reviewed encryption envelope.
- **Milestone 1:** the Keychain access group, accessibility class, file-protection class, and exact
  locked-device notification behavior.
- **Milestone 1:** whether CoreCrypto supports the required sequential cross-process open/close and
  whether its own file locking permits a narrower external lock scope.
- **Milestone 1:** how existing Apple CoreCrypto key/database versions are migrated, because the
  current Apple legacy-key migration path is not implemented.
- **Milestone 1:** the bounded legacy-WebSocket cutoff rule and whether consumable notifications are
  a deployment prerequisite for the NSE feature.
- **Milestone 1:** whether a valid MLS Welcome can be applied through a local-only no-send path. If
  not, Welcome processing remains foreground-only.
- **Milestone 1:** the concrete memory, binary-size, latency, backlog, and deadline safety budgets.
- **Before handoff schema implementation:** the lossless raw-envelope serialization, child ordering,
  idempotency-key derivation, retention window, maximum database size, and corruption recovery UX.
- **Before native integration:** the owner and refresh cadence of the notification-policy snapshot,
  its minimum fields, and the product fallback when it is missing or stale.
- **Before native integration:** whether the foreground synchronizer also stages all remote events
  through the handoff database or uses a transactionally equivalent native adapter before main-DB
  projection.
- **Before cursor cutover:** one-time migration, downgrade, logout/account-removal, multi-account,
  and extension/app version-skew behavior.
- **Before rollout:** the approved generic message and call fallback, push payload assumptions,
  notification replacement identifiers, diagnostics retention, and feature-flag/kill-switch owner.
- **Before framework distribution:** how `:domain:calling-notifications` and its native AVS symbols
  are exported and packaged, since the module is not currently published as a standalone artifact.
