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
| 0. Architecture and contracts | Completed | `167effa92142` | Source review and documentation consistency checks | ADR 0010 accepted for spike |
| 1. iOS feasibility spike | Completed for host/simulator scope | `56ba521dc210` | Host/simulator harness, symbol audit, split-framework probe, and report | Physical-device NSE/security/backend gates deferred |
| 2. Shared message receiving | Completed | `refactor: extract receive-only messaging primitives` | Metadata/Apple/logic compilation, dependency audit, simulator framework load, boundary review | Real payload tests deferred |
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

Commit signing policy: signed commits are preferred, but an unsigned commit is authorized whenever
the configured 1Password signing agent blocks milestone progress.

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

## Milestone 1 — iOS Feasibility Spike

Status: Completed for host/simulator scope on 2026-07-13

Planned deliverables:

- A disposable, non-production Apple feasibility harness using existing dependencies where possible.
- Build/link evidence for the narrow framework on available Apple targets.
- Probes for App Group-style shared paths, cross-process `flock`, Keychain configuration limits,
  CoreCrypto initialization/open-close feasibility, notification transport linkage, and
  notification-only AVS linkage.
- A measurement report that clearly separates verified simulator/host evidence from physical-device
  gates that require signing, entitlements, a backend account, push delivery, or an attached iPhone.
- A go/redesign/stop recommendation for each ADR Milestone 1 gate.

Verification constraints:

- Do not add or modify automated tests during the spike.
- Do not claim physical-device, push, locked-device, or production-backend verification without
  captured evidence from that environment.
- Prefer source/build probes over new third-party dependencies; any dependency addition must be
  called out separately before it is introduced.

Evidence recorded on 2026-07-13:

- Added the disposable `:sample:nse-feasibility` Apple module and the detailed report at
  `docs/spikes/ios-nse-milestone-1-feasibility.md`.
- macOS compile/executable link, simulator framework link, device `arm64` framework link, and a
  minimal Swift simulator framework-load probe passed.
- Caller-supplied root access passed.
- Host non-blocking `flock` contention returned in about 0.67 ms and the lock was reacquired in
  about 0.55 ms after the owning process was killed.
- CoreCrypto sequential open/close passed in about 29.2 ms when AVS was excluded.
- Darwin notification-engine/API linkage and the AVS factory/empty-input/close lifecycle passed
  separately.
- Combining the current notification AVS artifact with CoreCrypto in one macOS image reproducibly
  crashed with exit 139 in `EVP_DigestInit_ex -> HMAC_Init_ex -> PBKDF2 ->
  sqlcipher_openssl_kdf`.
- Archive and DWARF inspection proved that the macOS image mixed CoreCrypto OpenSSL with AVS
  BoringSSL across 2,068 overlapping global symbols. The audited iOS CoreCrypto archive instead
  uses SQLCipher CommonCrypto and has zero overlapping global definitions with the iOS AVS archive,
  so the macOS crash is not evidence of the same iOS collision.
- A separate AVS-only dynamic KMP framework and repeatable Swift host passed AVS -> CoreCrypto ->
  AVS and CoreCrypto -> AVS -> CoreCrypto sequences in both framework link orders. This is a
  provisional packaging direction, not an iOS/NSE runtime pass.
- AVS increased the simulator debug Mach-O by 11,578,784 bytes (+26.3%) and links the same broad
  native AVS archive used by full calling.
- No physical iPhone was attached. Signed NSE, App Group/Keychain entitlements, locked-device,
  APNs/backend, real message decryption, memory, and deadline gates remain open.

Current decision:

- Commit the verified host/simulator Milestone 1 spike and start Milestone 2.
- For the next signed iOS spike, keep CoreCrypto sync and notification AVS in separate dynamic
  frameworks and orchestrate them from Swift using only Swift-native scalars/bytes.
- Do not use linker-order or allow-multiple-definition workarounds. A genuinely notification-only
  AVS artifact with hidden/namespaced crypto remains preferred.
- Production readiness still requires a consuming Xcode app/NSE target, signing/entitlements, a
  physical iPhone, and backend/push/account inputs for the deferred ADR gates.

## Open Decisions

- Exact framework packaging and exported Swift API.
- App Group identifier and Keychain access-group configuration supplied by the consuming iOS app.
- Shared cursor ownership and representation.
- Handoff database encryption/key distribution mechanism.
- Behaviour for non-message events that must reach the foreground app.
- NSE fallback content when lock acquisition, network synchronization, or decryption cannot complete.
- Supported multi-account behaviour per NSE invocation.

## Milestone 2 — Shared Message Receiving

Status: Completed on 2026-07-13

Planned deliverables:

- Populate `:domain:messaging:receiving` with receive-only Proteus/MLS primitives and stable result
  contracts that do not depend on `:logic` event models or sending/recovery code.
- Split inbound cryptographic state advancement from full-app persistence, receipts, active MLS
  recovery, delayed commits, and notification rendering.
- Adapt full Kalium to consume the extracted primitives without changing its externally visible
  behavior.
- Keep protobuf decoding behind a narrow contract until its implementation is extracted in
  Milestone 3.
- Make the disposable NSE feasibility framework link the receiving module so the iOS Simulator
  verifies the intended dependency path.

Verification constraints:

- Do not add or modify automated tests during the spike.
- Run narrow common/Apple compilation and simulator framework-link checks only.
- Use a simulator framework-load/manual linkage probe; do not claim real Proteus/MLS message
  decryption without safe captured account state and payloads.
- Do not pull `:logic`, sending modules, the main user database, or active recovery into
  `:domain:messaging:receiving`.

Delivered:

- Added Event-neutral Proteus and MLS encrypted inputs, transaction-preserving decryptors, MLS
  receive results, a generic content-decoder boundary, Proteus external-content resolution, and
  exact decrypted protobuf retention to `:domain:messaging:receiving`.
- Added defensive copying for every public byte-bearing input/result.
- Adapted full Kalium Proteus and MLS receiving to consume the shared primitives while leaving
  conversation/group resolution, CRL processing, delayed proposal scheduling, persistence,
  receipts, sending, recovery, lifecycle, and notification rendering in `:logic`.
- Added a temporary `ProtoContentDecoderAdapter` so the large application mapper remains in
  `:logic` until Milestone 3.
- Linked `:domain:messaging:receiving` into the disposable NSE feasibility framework and added an
  explicitly non-decrypting linkage probe.

Verification evidence:

- `:domain:messaging:receiving:compileKotlinMetadata` passed.
- `:domain:messaging:receiving:compileKotlinIosSimulatorArm64` passed.
- `:logic:compileKotlinIosSimulatorArm64` passed.
- `:sample:nse-feasibility:linkDebugFrameworkIosSimulatorArm64` passed.
- The resolved receiving dependency graph contains `:core:cryptography` and its low-level
  transitives, with no Kalium network, persistence, database, `:logic`, or sending modules.
- A temporary Swift host installed and launched on the iPhone 16 Pro / iOS 18.4 simulator and
  printed `milestone2ReceivingLinked=true`, the four extracted contract/implementation names, and
  `realDecryption=false`; it then terminated cleanly.
- Independent boundary review found no correctness or dependency blocker and confirmed the
  Proteus callback/rollback boundary, MLS wrapper/CRL ordering, and proposal scheduling behavior
  were preserved.
- `git diff --check` and forbidden-import checks passed.
- Repository `detekt` passed after correcting the feasibility harness source names and linkage-probe
  constant.
- No tests were added, modified, or run.

Deferred production work:

- The low-level receiving APIs currently propagate crypto, decoder, AES, and callback exceptions.
  `:logic` retains existing failure classification. A stable receive-only failure taxonomy must be
  added before publishing the NSE API without pulling the current broad `:core:common` graph into
  the lightweight framework.
- Real Proteus/MLS payload, handshake, buffered-message, and rollback verification remains deferred
  by the spike's no-test rule and unavailable safe captured account state.

## Next Action

Commit Milestone 2, then start Milestone 3 in a fresh implementation thread. Split pure protobuf
decoding and notification-field extraction from the current application/sending mapper, preserve
exact protobuf bytes, and verify the new decoder through the iOS Simulator without adding or running
automated tests.
