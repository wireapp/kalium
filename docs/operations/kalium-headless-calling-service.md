# Kalium headless calling service operations guide

## Status and deployment gate

The `:logic:service` API and concrete JVM adapters are experimental. They are intended for
calling-team review and production-like integration, but must not be treated as stable or deployed
before the deferred validation below is complete.

Use one locally owned runtime per Wire identity and set
`-Pkalium.providerCacheScope=LOCAL`. A runtime has an explicit `start()` / `close()` lifecycle and
must not be shared between identities.

## Composition boundary

Create the complete JVM runtime with `WireKaliumService.create(config, observer)`. Its
`WireKaliumServiceConfig` requires an exact-identity `EncryptedJvmServiceStateStore`, durable
CoreCrypto locations and passphrases, restored self-conversation targets, server configuration,
CRL handling, and a call-event sink. The encrypted store must already contain the supplied durable
session. The lower-level `KaliumService.create(config, components, observer)` entry point remains
available for compatibility and custom deployments.

The service graph includes authenticated Wire networking, durable protocol state, event delivery,
conversation context, calling orchestration, CoreCrypto/MLS, and AVS. It intentionally excludes
chat and application-message persistence, message history, unread/UI state, full client sync,
call history and missed-call records, paging, backup, and Android WorkManager.

Calling signalling is not optional messaging. Incoming calling content is received through the
authenticated Wire event stream, decoded and decrypted before routing to AVS. Outgoing calling
content is encoded, encrypted, retained in an encrypted outbox, and sent directly through
`MessageApi` or `MLSMessageApi` by `WireEncryptedCallingSignalSender`. `WireCallTransport` uses
`CallApi` for call configuration and SFT requests.

## Required identity-scoped state

There are no no-op defaults for durable protocol state.

| Component | Required behavior |
| --- | --- |
| `ServiceSessionManager` | Restore and validate the durable token, user, backend, and client identity; create authenticated HTTP and WebSocket access; persist token refreshes before returning; close owned transports. |
| `ServiceCryptoRuntime` | Open the existing durable Proteus/CoreCrypto/MLS stores for the exact identity; fail instead of silently creating ephemeral replacement state; flush and close owned crypto resources. |
| `EventDeliveryStateStore` | Durably retain handled idempotency keys, pending acknowledgements, and acknowledged cursors with the ordering required by the event-processing contract. |
| `CallingEventIdempotencyStore` | Durably deduplicate the stable payload key across an event-handler/AVS crash window. |
| `AvsDeliveryJournal` | Reserve a native delivery before invoking AVS and persist acceptance before the outer handler completes. A restart that finds an unaccepted reservation fails closed until an operator establishes whether replay is safe. |
| `ConversationProtocolStateStore` | Durably retain the resolved conversation protocol, MLS group ID, and observed epoch needed after restart. |
| `ConferenceProtocolStateStore` | Durably retain enumerable parent/conference subgroup IDs and pending CRL work so restart and access-loss cleanup cannot be bypassed. |
| `DecryptedEventJournal` | Write a durable in-progress intent before crypto mutation, then retain the calling/protocol projection until required handlers and backend acknowledgement complete. An ambiguous in-progress entry fails closed. |
| `CallingSignalOutbox` | Retain a pre-encryption intent and then the exact encrypted payload until Wire accepts it; use the outbox ID as the stable Wire `GenericMessage` ID. |
| `PendingMlsCommitStore` | Durably retain first-proposal commit deadlines so CoreCrypto proposal delays survive restart and are committed before later MLS processing can continue after a scheduler failure. |
| `RequiredProtocolEventHandlers` | Process all retained Proteus and MLS protocol events, including welcome/reset and membership or client state that affects decryption and calling. An empty set cannot be installed. |

The concrete factory installs the event decoder/decryptor, remote conversation provider, explicit
self-conversation resolver, conference membership, AVS engine, and call-control handler. The host
still supplies CRL behavior and observability. Required state is encrypted at rest; access tokens,
plaintext protocol payloads, crypto secrets, and phone numbers must never be logged.

## Startup and readiness

`start()` performs these operations in order:

1. Restore and validate the durable session and Wire client identity.
2. Verify exact-identity markers and open durable crypto state.
3. Flush the encrypted signalling outbox, reconcile conference joins left by an earlier process,
   and restore pending MLS proposal timers.
4. Start the AVS calling engine.
5. Recover event delivery state and pending acknowledgement policy.
6. Open the authenticated event stream and wait for the source synchronization boundary.
7. Report `ServiceRuntimeState.READY`.

Calling commands are rejected until `READY`. Treat any `FAILED` transition as unavailable for new
joins; a leave request remains available so the host can release an active call.

An `EventSource` must return `EventStreamResult.Open` only after its authenticated stream is open
and its initial synchronization marker has been observed. A flow object by itself is not a
readiness signal.

## Delivery, acknowledgement, and restart rules

- Preserve the backend event envelope as one acknowledgement unit even when it contains multiple
  calling payloads. A decoded `GenericMessage` uses a sender-scoped stable message ID; legacy
  payloads fall back to `event-key#index`.
- Durably mark required protocol and calling work handled before attempting the backend
  acknowledgement.
- Advance the acknowledged cursor only after acknowledgement succeeds.
- Use `EventAcknowledgementRecovery.REPLAY` only when backend documentation guarantees the token is
  valid after reconnect.
- Use `WAIT_FOR_REDELIVERY` for WebSocket-session-scoped delivery tags. On restart, match the
  redelivered event by durable idempotency key and acknowledge the new tag. If it is absent from
  catch-up, the new synchronization boundary proves the prior acknowledgement was accepted before
  the durable cursor is advanced.
- Do not acknowledge a missed-notification/full-sync marker as recovered. The headless service has
  no full client sync; fail closed until a scoped protocol/conversation recovery implementation has
  completed successfully.

`NotificationApi.acknowledgeEvents` now returns `NetworkResponse<Unit>` after the frame is submitted
to the WebSocket, and `closeLiveEvents` explicitly closes the session. This is submission
confirmation, not an acknowledgement-of-acknowledgement from the backend. Consumable delivery tags
remain WebSocket-session scoped and use `WAIT_FOR_REDELIVERY`.

The concrete source holds at most one delivered event awaiting acknowledgement, buffers catch-up
within explicit event/payload limits until the synchronization marker, and reconnects transient
socket failures with bounded exponential backoff. It re-acknowledges an uncertain delivery only
after the same durable event key is redelivered (or a new synchronization marker proves the prior
acknowledgement crossed the old connection). Missed notifications and exhausted retry budgets fail
the runtime.

Acknowledged delivery records are committed before their decrypted-journal entries are removed.
Startup enumerates and prunes an acknowledged entry orphaned by a crash between those writes.
The encrypted delivery and calling-idempotency stores retain completed keys and fail at their
configured capacity. There is deliberately no unreviewed age-based eviction: operators must treat
capacity exhaustion as unavailable until a backend-checkpoint-aware compaction policy is approved.

## Calling operations

`runtime.calls.join(conversationId)` resolves current remote context, records the conversation
protocol mapping, joins conference membership where required, and starts the call through AVS.
`runtime.calls.leave(conversationId)` leaves AVS/conference state and clears in-memory active-call
state. The configured concurrency limit defaults to one active call.

The supplied AVS adapter passes the decrypted calling payload, sender identity, conversation,
message timestamp, and stable idempotency key to the native runtime. It coalesces duplicate delivery
within one native-handle lifetime and writes a durable intent before `wcall_recv_msg`. Explicit
native rejection or a failure before invocation releases the intent; native acceptance is persisted
before the handler completes. A crash-ambiguous intent is never replayed automatically. Stable
sender-scoped Wire message IDs also deduplicate an outbox resend delivered in a different backend
envelope. Crash injection at this non-transactional boundary remains required.

For untargeted Proteus sends, a definitive clients-changed response refreshes current recipients,
creates missing sessions, re-encrypts with the same stable message ID, persists the replacement,
and retries once. Timeouts and other uncertain failures retain the exact ciphertext.

`runtime.calls.recordAudio(path)` is diagnostic only. AVS writes process-global remote playout as
raw 16 kHz mono signed 16-bit PCM. It does not accept PSTN capture audio and is not a bidirectional
media bridge. PSTN/SIP media integration stays outside this composition until the calling team
defines a native media API.

## Shutdown and restart runbook

Always call `close()` from a non-abrupt service shutdown hook and observe its result. Shutdown
cancels event processing, closes the event source, waits up to `shutdownTimeoutMillis`, leaves and
closes calling/AVS state, closes crypto, and finally closes the authenticated session. Failed
resources remain eligible for a subsequent `close()` attempt. The timeout applies only to waiting
for the event-processing job; each supplied event-source, AVS, crypto, and session close operation
must enforce its own operational timeout.

After an unclean stop:

1. Keep the same durable identity, session, crypto, event-delivery, calling-idempotency, and
   protocol-mapping stores.
2. Start a new runtime for that identity; never reuse the closed instance.
3. Investigate any missed-notification signal before allowing joins.
4. Confirm backend redelivery or pending-ack replay according to the source recovery policy.
5. If startup reports an ambiguous in-progress crypto event, keep the service unavailable and follow
   the calling-team-approved recovery procedure; do not delete the marker and replay ciphertext.
6. If an AVS delivery intent is ambiguous, do not clear it until native/call evidence establishes
   that AVS did not accept the signalling. Releasing it authorizes one replay.
7. Confirm the runtime reaches `READY`, then resume PSTN routing.

## Observability

Implement `ServiceObserver` and `CallEventSink` without throwing. At minimum, emit safe metrics for
startup duration/failure stage, reconnects, event lag, decode/decrypt/handler failures,
acknowledgement latency/failures, duplicate calling payloads, join/leave outcome, active-call count,
SFT failures, MLS epoch changes, and cleanup failures. Use opaque runtime, conversation, and call
correlation values only.

## Verification commands

The composition graph and ABI baselines can be checked without running tests:

```bash
./gradlew :logic:service:verifyServiceModuleGraph :logic:client:verifyClientModuleGraph \
  -PUSE_UNIFIED_CORE_CRYPTO=true -Pkalium.providerCacheScope=LOCAL

./gradlew :data:events-api:checkKotlinAbi :data:conversation-api:checkKotlinAbi \
  :data:conversation-local:checkKotlinAbi :data:conversation-remote:checkKotlinAbi \
  :domain:event-processing:checkKotlinAbi :domain:conversation-runtime:checkKotlinAbi \
  :domain:calling-runtime:checkKotlinAbi :domain:calling-history:checkKotlinAbi \
  :logic:api:checkKotlinAbi :logic:runtime:checkKotlinAbi \
  :logic:service:checkKotlinAbi :logic:client:checkKotlinAbi :logic:checkKotlinAbi \
  -PUSE_UNIFIED_CORE_CRYPTO=true -Pkalium.providerCacheScope=LOCAL
```

The new reusable capability modules currently publish JVM and Apple targets and keep both JVM and
KLIB ABI baselines. JS publication is deferred in this JVM-first experimental pass because Kotlin
2.3.20 cannot merge JS and Native KLIB dump headers for a single artifact. The client composition
also checks its Android `KaliumClient` class signature separately because the current built-in ABI
task does not include the new Android KMP plugin target.

## Deferred validation and open decisions

No new tests, fixtures, or existing test changes are part of this pre-confirmation pass, and no
existing tests are claimed as run. Before stabilization, the calling team must confirm and the
implementation must add and run:

- restart at every event/checkpoint/acknowledgement boundary;
- disconnect/reconnect, redelivery, duplicate, multi-payload envelope, and missed-notification
  recovery;
- crash injection before, during, and after native AVS acceptance, durable MLS timer firing,
  acknowledged-journal cleanup, and Proteus client-churn re-encryption;
- durable session refresh and CoreCrypto/MLS restore without accidental reprovisioning;
- remote conversation and self-conversation resolution for Proteus and MLS;
- encrypted incoming and outgoing signalling through real Wire APIs;
- Proteus and MLS join/leave, control idempotency, concurrency, and load;
- shutdown during an active call with native resource-leak checks;
- full-client regression, performance, Android, Apple/XCFramework, and compatibility-facade
  coverage.

Open deployment decisions are the supported protocol set, calls per identity/process, identity and
client provisioning owner, durable-store implementation, delivery guarantee, missed-notification
recovery policy, conversation cache policy, required metrics/SLOs, release line, and load target.
The AVS binding now exposes per-handle `wcall_destroy`, ref-counted process `wcall_close`, MLS epoch
updates, client updates, and diagnostic recording; their final ownership contract still needs
calling-team confirmation. CoreCrypto state and the encrypted event journal are separate durable
stores. A durable pre-mutation intent prevents silent ciphertext replay, but an interrupted mutation
is intentionally operator-recoverable rather than automatically replayed; crash injection and the
final recovery policy remain a production gate. The transitional
`:logic:client -> :logic` dependency must be reversed only with the deferred client
regression/performance and Swift/XCFramework validation.
