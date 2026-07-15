# Kalium headless calling service operations guide

## Status and deployment gate

The `:logic:service` API is experimental. It is intended for calling-team review and
production-like integration, but it must not be treated as stable or deployed without concrete
production adapters and the deferred validation listed below.

Use one locally owned runtime per Wire identity and set
`-Pkalium.providerCacheScope=LOCAL`. A runtime has an explicit `start()` / `close()` lifecycle and
must not be shared between identities.

## Composition boundary

Create the JVM runtime with `KaliumService.create(config, components, observer)`. The
`KaliumServiceComponents` bundle must be constructed for the same `ServiceIdentity` as the config.
Startup rejects a mismatched bundle.

The service graph includes authenticated Wire networking, durable protocol state, event delivery,
conversation context, calling orchestration, CoreCrypto/MLS, and AVS. It intentionally excludes
chat and application-message persistence, message history, unread/UI state, full client sync,
call history and missed-call records, paging, backup, and Android WorkManager.

Calling signalling is not optional messaging. Incoming calling content is received through the
authenticated Wire event stream, decoded and decrypted before routing to AVS. Outgoing calling
content is encoded, encrypted, and sent through `MessageApi` or `MLSMessageApi` by the supplied
`EncryptedCallingSignalSender`. `WireCallTransport` uses `CallApi` for call configuration and SFT
requests.

## Required identity-scoped state

There are no no-op defaults for durable protocol state.

| Component | Required behavior |
| --- | --- |
| `ServiceSessionManager` | Restore and validate the durable token, user, backend, and client identity; create authenticated HTTP and WebSocket access; persist token refreshes before returning; close owned transports. |
| `ServiceCryptoRuntime` | Open the existing durable Proteus/CoreCrypto/MLS stores for the exact identity; fail instead of silently creating ephemeral replacement state; flush and close owned crypto resources. |
| `EventDeliveryStateStore` | Durably retain handled idempotency keys, pending acknowledgements, and acknowledged cursors with the ordering required by the event-processing contract. |
| `CallingEventIdempotencyStore` | Durably deduplicate the stable payload key across an event-handler/AVS crash window. |
| `ConversationProtocolStateStore` | Durably retain the resolved conversation protocol, MLS group ID, and observed epoch needed after restart. |
| `RequiredProtocolEventHandlers` | Process all retained Proteus and MLS protocol events, including welcome/reset and membership or client state that affects decryption and calling. An empty set cannot be installed. |

The service host also supplies the event decoder/decryptor, remote conversation context provider,
self-conversation resolver, conference membership, AVS engine, call-control handler, and optional
observability sink. Required state must be encrypted at rest and access tokens, plaintext protocol
payloads, crypto secrets, and phone numbers must never be logged.

## Startup and readiness

`start()` performs these operations in order:

1. Restore and validate the durable session and Wire client identity.
2. Open durable crypto state.
3. Start the AVS calling engine.
4. Recover event delivery state and pending acknowledgement policy.
5. Open the authenticated event stream and wait for the source synchronization boundary.
6. Report `ServiceRuntimeState.READY`.

Calling commands are rejected until `READY`. Treat any `FAILED` transition as unavailable for new
joins; a leave request remains available so the host can release an active call.

An `EventSource` must return `EventStreamResult.Open` only after its authenticated stream is open
and its initial synchronization marker has been observed. A flow object by itself is not a
readiness signal.

## Delivery, acknowledgement, and restart rules

- Preserve the backend event envelope as one acknowledgement unit even when it contains multiple
  calling payloads. Each calling payload uses a stable `event-key#index` idempotency key.
- Durably mark required protocol and calling work handled before attempting the backend
  acknowledgement.
- Advance the acknowledged cursor only after acknowledgement succeeds.
- Use `EventAcknowledgementRecovery.REPLAY` only when backend documentation guarantees the token is
  valid after reconnect.
- Use `WAIT_FOR_REDELIVERY` for WebSocket-session-scoped delivery tags. On restart, match the
  redelivered event by durable idempotency key and acknowledge the new tag.
- Do not acknowledge a missed-notification/full-sync marker as recovered. The headless service has
  no full client sync; fail closed until a scoped protocol/conversation recovery implementation has
  completed successfully.

The existing public `NotificationApi.acknowledgeEvents` returns `Unit` and does not expose a
truthful acknowledgement-success result or an explicit transport close. A production event-source
adapter must therefore own transport behavior that can satisfy `EventSourceResult` and `close()`;
do not report success merely because an acknowledgement was queued.

## Calling operations

`runtime.calls.join(conversationId)` resolves current remote context, records the conversation
protocol mapping, joins conference membership where required, and starts the call through AVS.
`runtime.calls.leave(conversationId)` leaves AVS/conference state and clears in-memory active-call
state. The configured concurrency limit defaults to one active call.

The supplied AVS adapter must pass the decrypted calling payload, sender identity, conversation,
message timestamp, and stable idempotency key to the native runtime. The control handler must also
deduplicate completed control actions because a process can stop after AVS succeeds but before the
durable idempotency record is updated.

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
5. Confirm the runtime reaches `READY`, then resume PSTN routing.

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
The AVS binding also needs calling-team confirmation of native handle ownership because it exposes
no per-handle destroy operation. The transitional `:logic:client -> :logic` dependency must be
reversed only with the deferred client regression/performance and Swift/XCFramework validation.
