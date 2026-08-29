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
