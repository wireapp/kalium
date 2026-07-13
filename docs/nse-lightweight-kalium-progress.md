 Lightweight Kalium for iOS NSE — Spike Progress

Last updated: 2026-07-13

## Working Agreement

- This work is an architectural and implementation spike.
- Do not add or modify tests during the spike. Tests are deliberately deferred until the end-to-end design has been verified.
- Use a fresh implementation thread for each milestone. Use separate review threads when the change benefits from independent review.
- Verify each milestone with the narrowest applicable non-test checks, builds, source inspection, and spike demonstrations.
- Update this document before committing each milestone.
- Commit each completed and verified milestone separately before starting the next milestone.
- Do not include unrelated working-tree changes in milestone commits.

## Target Outcome

Provide a lightweight Kalium framework for an iOS Notification Service Extension that can:

- Run a bounded incremental WebSocket synchronization.
- Coordinate synchronization exclusively with the foreground app across processes.
- Use notification-only AVS handling.
- Use CoreCrypto for background decryption.
- Store decrypted protobuf blobs in a separate App Group handoff database.
- Let the foreground app idempotently import handoff events into its main database.
- Exclude message sending, read/delivery receipts, and active MLS sending recovery.

## Milestone Status

| Milestone | Status | Commit | Verification | Notes |
| --- | --- | --- | --- | --- |
| 0. Architecture and contracts | Completed | `docs: define lightweight Kalium NSE spike architecture` | Source review and documentation consistency checks | ADR 0010 accepted for spike |
| 1. iOS feasibility spike | Not started | — | — | — |
| 2. Shared message receiving | Not started | — | — | — |
| 3. Protobuf decoding and notification extraction | Not started | — | — | — |
| 4. Bounded incremental-sync engine | Not started | — | — | — |
| 5. Cross-process coordination | Not started | — | — | — |
| 6. Shared handoff database | Not started | — | — | — |
| 7. Lightweight NSE framework | Not started | — | — | — |
| 8. Foreground importer contract/integration | Not started | — | — | — |
| 9. Resilience, security, and performance | Not started | — | — | — |
| 10. Rollout and observability | Not started | — | — | — |

## Milestone 0 — Architecture and Contracts

Status: Completed on 2026-07-13

Planned deliverables:

- ADR for module boundaries, event lifecycle, process locking, handoff persistence, and foreground import.
- Explicit spike scope and non-goals.
- Dependency direction that respects `core -> data -> domain -> logic`.
- ACK, cursor, cancellation, and crash-recovery invariants.
- iOS App Group and Keychain sharing requirements.
- Milestone 1 feasibility checklist and measurable exit criteria.

Verification evidence:

- A dedicated architecture thread inspected the existing incremental sync, event persistence,
  CoreCrypto receive path, notification-only AVS boundary, and Apple persistence configuration.
- An independent source-review thread verified the current ACK, cursor, raw-event buffering,
  CoreCrypto transaction, Apple database, and Keychain assumptions.
- The review confirmed that current Kalium persists before attempting a transport ACK, advances its
  cursor separately before decryption/import completes, and cannot reuse its unbounded/
  `NonCancellable` incremental worker directly in an NSE.
- `git diff --check` and repository-relative path checks are required immediately before commit.
- No build is required because this milestone changes documentation only.

Deferred tests:

- All automated tests are deferred by the spike working agreement.

## Decisions and Invariants

- The NSE performs bounded work and never owns an infinite synchronization loop.
- The app and NSE use the same per-account cross-process lock.
- The NSE attempts lock acquisition without waiting; lock contention produces a safe fallback.
- Transport ACK is distinct from chat delivery and read receipts. Transport ACK remains required.
- An event is transport-ACKed only after durable local persistence.
- Decrypted protobuf bytes are preserved unchanged in the handoff database.
- Foreground import is idempotent and marks a handoff row imported only after the main database commit succeeds.
- Passive MLS state advancement required for receiving remains in scope.
- Sending, delivery/read receipts, and active MLS sending recovery are out of scope.
- Notification-only AVS is used; the full calling stack is excluded.

## Open Decisions

- Exact framework packaging and exported Swift API.
- App Group identifier and Keychain access-group configuration supplied by the consuming iOS app.
- Shared cursor ownership and representation.
- Handoff database encryption/key distribution mechanism.
- Behaviour for non-message events that must reach the foreground app.
- NSE fallback content when lock acquisition, network synchronization, or decryption cannot complete.
- Supported multi-account behaviour per NSE invocation.

## Next Action

Commit Milestone 0, then start Milestone 1 in a fresh implementation thread. Milestone 1 must prove
the Apple platform assumptions on a physical iPhone where available and record measurements rather
than adding production architecture prematurely.
