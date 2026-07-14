# 10. Split the SDK by capability

Date: 2026-07-14

## Status

Accepted for an experimental implementation. The public service API remains changeable until the
calling team confirms the integration contracts and the deferred test plan is implemented.

## Context

Kalium's `:logic` artifact is both the client-facing SDK and the composition root for networking,
local databases, synchronization, messaging, calling, backup, and background work. That shape is
appropriate for full clients, but it prevents a backend service from using the Wire calling path
without also installing client-only message history, unread state, full synchronization, call
history, and Android work behavior.

The first backend consumer is a PSTN service. It still needs the complete encrypted Wire calling
transport: authenticated HTTP and WebSocket access, durable Wire identity and session state,
CoreCrypto/MLS state, event delivery and idempotency state, inbound calling-event decryption and
routing to AVS, and encrypted outbound calling signalling. "No messaging" only excludes chat and
application-message features; it does not exclude calling payloads carried through Wire's message
transport.

Calling currently obtains operational conversation data and persistence behavior through the large
client composition. Active call state, call history, missed-call messages, event delivery, and chat
message persistence therefore cannot be selected independently.

## Decision

Split Kalium by reusable capability while preserving the existing dependency direction:

```text
core -> data -> domain -> logic
```

Storage-neutral event-processing contracts will separate delivery, acknowledgement/checkpointing,
decoding, decryption, routing, and optional feature handlers. Conversation runtime contracts will
provide the minimum operational context required by calling. Calling runtime contracts will make
transport, conference membership, active state, lifecycle events, and optional history explicit.

Top-level compositions select the installed capabilities:

- `:logic:client` provides the existing full client behavior.
- `:logic:service` provides a JVM-first headless calling runtime.
- `:logic` remains the compatibility facade for existing client consumers.

The initial service composition uses one locally owned runtime per Wire identity, explicit
`start()` and idempotent `close()` lifecycle, in-memory active call state, and service-supplied
durable identity/session, crypto, and event checkpoint/idempotency state. Required durable state
has no no-op default. Provider caches remain local to a runtime.

The service supports the existing call protocols unless evidence and calling-team review approve a
narrower contract. It can receive and send encrypted calling signalling through Wire APIs. It does
not install chat/application-message history, unread or UI state, full client sync, call history,
missed-call message persistence, backup, paging, or Android WorkManager behavior.

Dependencies remain explicit through constructor injection. No dependency-injection framework or
new external library is introduced. New service-facing APIs are marked experimental or internal
until the calling team confirms the final shape.

Implementation follows Phases 0 through 6 in the detailed proposal. During this pre-confirmation
pass, production compilation, static analysis, module-graph inspection, and ABI checks replace the
new test work described by the proposal. New tests, fixtures, and changes to existing tests are
deferred and must be completed before stabilization.

## Consequences

**Easier:**

- Backend services can install encrypted Wire calling transport without client chat persistence.
- Event acknowledgement and idempotency become explicit instead of being hidden in a client
  repository.
- Calling can use local or remote operational conversation context without depending on full sync.
- Active call behavior can be reused while client-only history remains an adapter.
- The existing client API can migrate behind compatibility composition without a flag-filled fork.

**More difficult:**

- More module and lifecycle boundaries must be maintained and reviewed.
- Service integrators must provide secure durable session, client identity, CoreCrypto/MLS, and
  event delivery state.
- Restart and duplicate-delivery correctness cannot be assumed from an in-memory runtime.
- The experimental API may change after calling-team and production-like integration feedback.

**Deferred confirmation and validation:**

- Confirm supported call protocols and AVS media ownership with the calling team.
- Confirm runtime concurrency limits, retry policy, and operational metrics.
- Confirm the concrete durable-store implementations used by the PSTN deployment.
- Implement the module, composition, integration, restart, reconnect, duplicate-event, and load
  tests described by the proposal after calling-team confirmation.
- Decide when the service API can move from experimental to stable.

The detailed rationale, module responsibilities, rollout phases, risks, and open questions are in
[`../proposals/kalium-sdk-for-clients-and-services.md`](../proposals/kalium-sdk-for-clients-and-services.md).
