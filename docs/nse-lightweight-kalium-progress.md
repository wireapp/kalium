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
| 2. Shared message receiving | Completed | `1e674b5bf901` | Metadata/Apple/logic compilation, dependency audit, simulator framework load, boundary review | Real payload tests deferred |
| 3. Protobuf decoding and notification extraction | Completed | `b96db61ff87e` | Metadata/Apple/logic compilation, dependency audit, simulator protobuf probe, boundary review | Policy and API hardening deferred |
| 4. Bounded incremental-sync engine | Completed | `feat: add bounded notification sync engine` | Metadata/Apple compilation, dependency audit, simulator state-machine probe, boundary review | Concrete adapters and real backend deferred |
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

## Milestone 3 — Protobuf Decoding and Notification Extraction

Status: Completed on 2026-07-13

Planned deliverables:

- Extract inbound protobuf decoding from the large application/sending mapper into a lower-layer,
  receive-only component that has no direct dependency on `:logic`, persistence, network, sending,
  MLS recovery, or AVS.
- Define a stable lightweight received-content representation for notification-relevant message
  forms while retaining exact original protobuf bytes in the existing receive result.
- Add a pure notification-content extractor for text, replies/quotes, assets, edits/deletes,
  notification-relevant reactions and calling payloads, with an explicit safe outcome for
  unsupported or future content.
- Adapt full Kalium to use the extracted decoder without changing its existing application-facing
  message mapping behavior.
- Link the decoder/extractor into the disposable NSE feasibility framework and expose a bounded
  manual simulator probe.

Verification constraints:

- Do not add, modify, or run automated tests during the spike.
- Use narrow metadata and iOS Simulator compilation, dependency inspection, framework linking, and
  a manual Swift simulator host probe.
- Preserve full-app decode semantics and ordering; do not move database writes, receipts, sending,
  recovery, notification presentation, or platform UI concerns into the shared component.
- Unknown or future protobuf content must produce an explicit unsupported result and must not crash
  notification extraction.
- Do not claim real encrypted-message coverage unless safe captured account state and payloads are
  available; a locally constructed protobuf linkage probe is sufficient for this milestone.

Delivered:

- Added the narrow `:data:message-content` module with only `:core:data` and `:data:protobuf` as
  direct project dependencies.
- Extracted the full receive-only `GenericMessage` decoder while retaining the exact serialized
  protobuf through a defensive copy-owning result.
- Added explicit application, external-instruction, and unsupported/future classifications without
  changing the full app's existing `Ignored`/`Unknown` mapping behavior.
- Added a pure notification extractor with structured candidates for text, quotes, self mentions,
  assets, multipart content, edits, deletes, reactions, calling payloads, knocks, and locations.
  Candidate metadata includes legal-hold status and expiry; unresolved external content, known
  non-notifiable content, and unsupported content have separate outcomes.
- Adapted the full `ProtoContentMapper` to delegate inbound decoding to the shared decoder while
  retaining its application/sending encoder.
- Added a locally constructed text/quote and unsupported-content linkage probe to the disposable
  Apple feasibility framework.

Verification evidence:

- `:data:message-content:compileKotlinMetadata` passed.
- `:data:message-content:compileKotlinIosSimulatorArm64` passed.
- `:logic:compileKotlinIosSimulatorArm64` passed.
- `:sample:nse-feasibility:linkDebugFrameworkIosSimulatorArm64` passed.
- The iPhone 16 Pro / iOS 18.4 simulator reported `milestone3ContentLinked=true` with
  `text=true`, `quote=true`, `exactBytes=true`, `unsupportedSafe=true`, and
  `policyEvaluation=false`.
- The direct project dependency audit contains only `:core:data` and `:data:protobuf`. The resolved
  graph also records the accepted residual that `:core:data` currently brings
  `:data:network-model` and Ktor transitively.
- Independent review found no correctness or module-boundary blocker and confirmed semantic parity
  with every former inbound decoder branch; only diagnostic logging changed.
- Repository `detekt`, `git diff --check`, forbidden direct-import checks, and the no-test-file audit
  passed.
- No tests were added, modified, or run.

Deferred production work:

- A structural notification `Candidate` still requires the later policy snapshot to approve
  privacy, mute, sender, conversation, and content-specific display. Reactions, deletes, and
  composite edits must not be rendered merely because extraction succeeded.
- The existing `:core:data` boundary keeps network-model/Ktor in the resolved graph. A smaller
  stable model boundary can be considered during final framework-size hardening if dead-code
  elimination and measured size are insufficient.
- Public collection data-class constructors are not deeply immutable against caller-owned mutable
  collections or downcasts, although extractor-created attachment lists and reaction sets are
  detached from decoded state.
- Legacy `ProtoContent.ExternalMessageInstructions` exposes mutable key/hash arrays. The exact
  retained serialized protobuf remains protected, but a published NSE API should use a dedicated
  copy-owning external-instruction value.
- Malformed protobuf remains a decode failure by design and needs a stable receive-only failure
  taxonomy before production publication.

## Milestone 4 — Bounded Incremental-Sync Engine

Status: Completed on 2026-07-13

Planned deliverables:

- Extract a finite incremental-sync run behind narrow transport, durable staging, transport-ACK,
  and cursor contracts without moving the existing `EventRepository`, `EventMapper`, or unbounded
  worker wholesale into the lightweight graph.
- Require an explicit deadline and bounded event/batch budgets, with cancellation and concrete
  terminal outcomes for caught up, deadline reached, budget exhausted, contention/deferred input,
  transport closure, and failure.
- Preserve durable raw-event storage before transport ACK and make cursor advancement explicit so
  it cannot outrun committed local staging.
- Keep transport ACK separate from chat delivery/read receipts and exclude message sending, active
  MLS recovery, application lifecycle, and infinite retry behavior.
- Adapt full Kalium only where the bounded primitive can be consumed without changing its existing
  continuous foreground behavior.
- Link a deterministic in-memory/manual bounded-run probe into the disposable Apple feasibility
  framework and exercise it through the iOS Simulator where practical.

Verification constraints:

- Do not add, modify, or run automated tests during the spike.
- Use narrow metadata and iOS Simulator compilation, dependency inspection, simulator/manual probe,
  source-level invariant review, `detekt`, and diff checks.
- Do not introduce the cross-process file lock, encrypted handoff database implementation, Swift
  importer, or production cursor cutover in this milestone; define only the contracts the engine
  needs for later composition.
- Do not reuse the current unbounded/`NonCancellable` worker as the NSE execution path.

Delivered:

- Added the standalone `:domain:notification-sync` module with no Kalium network, persistence,
  `:logic`, sending, recovery, or application-lifecycle dependency.
- Added one finite consumable-notification catch-up with an absolute deadline, close safety margin,
  transport-frame/event/drain-batch budgets, cancellable suspend boundaries, and no retry loop or
  `NonCancellable` region.
- Added explicit lock/lease, cursor, transport-session, raw-inbox, ACK, receive-processing, and
  foreground-deferral contracts for later concrete adapters.
- Required one atomic `stageRawEventAndAdvanceCursor` operation. Non-transient events carry an
  explicit opaque cursor independent from their event key; transient events stage without cursor
  advancement. Exact scoped key, raw bytes, transient flag, and cursor metadata determine an
  idempotent duplicate, while any mismatch is a terminal integrity conflict.
- Required copy-owned raw envelope bytes and kept delivery tags and marker IDs outside durable raw
  event values.
- Required durable staging before a transport ACK can be enqueued. ACK acceptance only transfers
  responsibility to the active local writer and cannot be discarded by immediate session close; it
  does not claim backend confirmation or a chat receipt.
- Validated the current marker before marker ACK/completion, left mismatched markers unacknowledged,
  and deferred missed-notification and unproven legacy catch-up paths to foreground recovery.
- Added deterministic, snapshot-consistent bounded drain batches, idempotent receive
  materialization, per-event foreground deferral, and separate global recovery state while retaining
  raw rows for later foreground import.
- Guaranteed nested non-suspending session close and lease release on every return, timeout, or
  external cancellation. Port contracts own resource cleanup for acquisition/open races.
- Kept the current full-app `EventDataSource` unchanged because its separate insert, ACK, and cursor
  calls cannot truthfully implement the new atomic contract.

Verification evidence:

- `:domain:notification-sync:compileKotlinMetadata` passed.
- `:domain:notification-sync:compileKotlinIosSimulatorArm64` passed.
- `:sample:nse-feasibility:linkDebugFrameworkIosSimulatorArm64` passed.
- The iPhone 16 Pro / iOS 18.4 simulator reported `milestone4BoundedSync=true` and proved marker
  completion after the final allowed event, deadline rejection before lease acquisition, a second
  event stopped without staging or ACK after budget exhaustion, durable stage before ACK, atomic
  cursor movement, raw retention, bounded closure, and lease release. No real network was used.
- The resolved module graph contains Kotlin stdlib, coroutines, and datetime only, with no Kalium
  network, persistence, `:logic`, sending, or recovery module.
- Independent state-machine review found no remaining blocker after explicit cursor, duplicate/
  conflict, cancellation-race ownership, per-event deferral, snapshot `hasMore`, nested cleanup, and
  terminal-result fixes.
- Repository `detekt`, `git diff --check`, dependency/import checks, and the no-test-file audit
  passed.
- No tests were added, modified, or run.

Deferred production work:

- The existing `NotificationApi` ACK method returns `Unit`; a concrete transport adapter must prove
  the stronger local-writer acceptance and immediate-close guarantee before it can be used here.
- The non-blocking cross-process lock and encrypted shared inbox/cursor implementations remain
  Milestones 5 and 6.
- Legacy WebSocket/pending-page cutoff remains foreground-only until its race and bounded drain
  behavior are proven.
- Stable event-key uniqueness beyond the account scope and backend event ID needs protocol audit.
- Real backend, decryption, physical-device, locked-device, and native-operation timing remain
  unverified.

## Next Action

Commit the verified Milestone 4 change, then start Milestone 5 in a fresh implementation thread.
Implement the stable per-account non-blocking Apple `flock` lease behind the new coordinator
contract, prove contention and process-death release behavior, and avoid adding or running tests.
