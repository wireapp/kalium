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
| 4. Bounded incremental-sync engine | Completed | `919510813e62` | Metadata/Apple compilation, dependency audit, simulator state-machine probe, boundary review | Concrete adapters and real backend deferred |
| 5. Cross-process coordination | Completed | `7cbe26a3f797` | Apple compilation/linking, simulator lifecycle probe, macOS multi-process/owner-death probe, security-path probes, dependency audit, independent review, detekt, diff check | Signed App Group and physical-device gates deferred |
| 6. Shared handoff database | Completed | `00fdd8259877` | Metadata/JVM/Android/Apple compilation, Apple links, macOS and simulator durability/rollback probes, dependency audit, independent security review, detekt, diff check | Encrypted production factory and device gates deferred |
| 7. Lightweight NSE framework | Completed | `b5a36b47409e` | Apple framework links, application-extension Swift compile, iOS simulator probe, dependency/header/symbol audits, detekt, independent review | Production adapters remain gated by the deferred M1/M4/M5/M6 security and backend work |
| 8. Foreground importer contract/integration | Completed | `7a098a341c5b` | Kotlin/Swift compile, KMP/native probes, token parity, dependency audit, detekt, independent review | Real native app mapping and production storage/path/device gates remain open |
| 9. Resilience, security, and performance | Completed | `f7dff7073677` | macOS/simulator hardening probes, Swift token/import parity, bounded-sync budget probe, Apple/metadata compilation | External native app, encrypted storage, signing, backend, physical-device, and product budget approvals remain explicit |
| 10. Rollout and observability | Implementation and verification complete; awaiting commit | — | Metadata/Apple compilation, simulator framework/Swift host probe, dependency/header/privacy audits, detekt, independent review | External rollout ownership, privacy approval, signing, backend, and physical-device validation remain explicit |

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

## Milestone 5 — Cross-Process Coordination

Status: Completed on 2026-07-13

Delivered:

- Added dependency-free `:data:sync-coordination` common acquisition results, typed retryable and
  terminal failures, and an idempotent non-throwing lease contract. It has no dependency on domain,
  logic, persistence, networking, or message sending.
- Added `AppleProcessLockFactory` with the ADR-aligned cross-language layout
  `<sharedRoot>/kalium-nse/v1/<digest>/sync.lock`. The SHA-256 digest uses versioned, length-prefixed
  UTF-8 account/client identifiers and never exposes raw identifiers in the path. The published
  `probe-account`/`probe-client` vector is
  `9c03173842651044f0848cfb08e7ef905916c4eae2d198cb7ab691d9124ee5ba`.
- Added an iOS implementation that anchors the root with `O_NOFOLLOW_ANY`, creates and opens fixed
  descendants relative to retained directory descriptors, rejects symlinks, validates effective-user
  ownership/type/exact `0700` and `0600` modes plus a single lock-file link, and acquires only with
  `flock(LOCK_EX | LOCK_NB)`.
- Ownership is the retained file descriptor, not file existence. Contention returns immediately,
  failures close all pre-transfer descriptors, release atomically unlocks/closes at most once, and
  the lock file is deliberately retained after release and process termination.
- Added a macOS host-parity implementation and changed the feasibility harness to invoke the
  production primitive. The host implementation uses full-path `O_NOFOLLOW_ANY` because Kotlin/Native
  excludes `openat` from its macOS bindings; it is evidence for `flock` behavior, not for the iOS
  descriptor-ancestry implementation.

Verification evidence:

- Module metadata, iOS Simulator ARM64, and macOS ARM64 compilation passed; the iOS feasibility
  framework and macOS executable linked; root `detekt` and `git diff --check` passed.
- The iPhone 16 Pro / iOS 18.4 simulator acquired, observed in-process contention, released twice,
  reacquired, and retained the lock file. On-disk descendants were `0700`; the `0600` lock file had
  one link.
- A real macOS holder and contender returned contention in 765,000 ns. After the holder was killed
  with `SIGKILL`, the same lock was reacquired in 685,000 ns.
- Precreated symlink, directory-at-lock-path, and hard-linked lock entries returned terminal
  `UNSAFE_FILE_SYSTEM_ENTRY`; a root containing `..` returned terminal `INVALID_SHARED_ROOT`.
- Dependency/import and independent security/correctness reviews found no remaining blocker.
- No tests were added, modified, or run, per the spike instruction.

Deferred production work:

- Verify signed app-versus-NSE contention, App Group entitlement authenticity, protection-class and
  locked-device behavior on a physical device.
- The later higher-layer adapter to `NotificationSyncLeaseCoordinator` must check cancellation before
  and immediately after native acquisition, releasing immediately if cancellation wins.
- App and NSE must supply identical canonical protocol account/client IDs. Cleanup code must never
  rename or remove the private lock tree outside the same lock contract, because cooperating
  same-entitlement code can otherwise create split lock-file inodes.
- The shared inbox, foreground importer, authentication refresh adapter, and production App Group
  wiring remain outside this milestone.

## Milestone 6 — Shared Handoff Database

Status: Completed on 2026-07-13

Delivered:

- Added dependency-light `:data:notification-inbox` with no domain, logic, network, main-persistence,
  sending, or CoreCrypto dependency. Its common contract owns raw/protobuf byte arrays defensively
  and leaves adaptation to `NotificationSyncInbox` for the lightweight assembly milestone.
- Added a versioned SQLDelight cross-language schema for contract/storage metadata, one durable
  account/client cursor, raw event parents, scoped receive children, global recovery signals, and
  independent receive/notification/foreground-import lifecycle state.
- Defined raw-envelope format v1 as the complete UTF-8 JSON `data.event` value captured before DTO
  decoding, retaining unknown fields and excluding transport delivery tags, markers, and wrappers.
- Scoped raw identity by account/client/server event ID. Exact duplicate certification compares
  owned bytes, SHA-256, format, transient state, and cursor metadata; any mismatch fails as an
  integrity conflict.
- Inserted a non-transient raw event and advanced its explicit opaque cursor in one transaction.
  Transient events never move the cursor, and replaying an older exact row cannot regress a cursor
  whose source ingest sequence is newer.
- Added stable bounded pending reads with `limit + 1` snapshot semantics, per-blob/schema limits,
  aggregate batch limits, SHA-256 recomputation, enum/relationship validation, and copy-owned
  return values before data crosses the storage boundary.
- Added domain-separated, deterministic child fallback IDs and account/client-scoped protocol UID
  IDs. A complete ordered child set and parent receive completion commit in one transaction;
  exact replay is idempotent and immutable-content conflicts abort the entire batch.
- Added raw-parent and child foreground-read surfaces without importing into or marking rows
  imported in the application's main database. Zero-child/non-message and deferred raw parents
  remain available for the later native importer.
- Added an Apple synthetic-only plaintext factory with fixed synthetic account/client IDs, a
  `PLAINTEXT_SYNTHETIC_SPIKE_V1` metadata marker, DELETE journaling, foreign keys, zero busy wait,
  one reader, conservative synchronous writes, and payload-safe silent driver logging. There is no
  production Apple construction boundary while encryption and file protection remain unresolved.
- Added JVM, Android, and Apple SHA-256 implementations without adding a hashing dependency, plus
  disposable Apple probes using locally constructed JSON and valid `GenericMessage` protobufs.

Verification evidence:

- `:data:notification-inbox` metadata, JVM, Android, iOS Simulator ARM64, and macOS ARM64 compilation
  passed. The iOS feasibility framework and macOS feasibility executable linked.
- The iPhone 16 Pro / iOS 18.4 simulator and macOS probe both reported raw/cursor atomicity,
  duplicate/conflict detection, transient staging without cursor movement, old-replay cursor
  non-regression, stable bounded ordering, complete child-batch atomicity, exact raw/protobuf BLOB
  retention, and close/reopen durability.
- Synthetic one-shot failures after raw insertion but before cursor update and after all child
  inserts but before parent completion rolled back the complete transactions. Absence was verified
  immediately and after close/reopen while the Milestone 5 account lock covered the full lifecycle.
- Repository `detekt`, `git diff --check`, untracked whitespace checks, dependency/import checks,
  and the no-test-file audit passed. The resolved common graph contains SQLDelight runtime/async,
  coroutines, Kotlin stdlib, and atomicfu only.
- Independent schema and transaction/security reviews found no remaining blocker inside the
  explicitly synthetic spike scope.
- No tests were added, modified, or run.

Deferred production work:

- The current Apple native SQLite driver is plaintext and cannot store real decrypted protobufs.
  Production still requires a reviewed SQLCipher/encrypted driver, shared Keychain access group,
  compatible file-protection class, and no-follow/path-hardening construction under the App Group.
- Define and enforce retention plus a total database-and-sidecar size policy. This spike deliberately
  never deletes rows and treats `SQLITE_FULL` as a storage failure without acknowledging transport.
- Prove and implement CoreCrypto-to-handoff crash ordering because crypto state and this database
  cannot share one transaction, especially for buffered MLS outputs and handshakes.
- Implement the M7 data-to-domain adapter, secure account-digest handoff path, notification-policy
  snapshot ownership, and real transport capture of raw-envelope format v1.
- Implement the foreground import/mark-imported protocol, authoritative cursor migration and
  downgrade/account-removal policy, and cleanup under the same process lock.
- Signed app-versus-NSE, App Group, locked-device, physical-device, real payload, and production
  backlog/deadline behavior remain unverified.

## Milestone 7 — Lightweight NSE Framework

Status: Completed on 2026-07-13 in `b5a36b47409e`

Delivered:

- Added `:logic:notification-extension` as the narrow dynamic Swift-facing core framework and
  `:logic:notification-extension-avs` as a separate dynamic notification-AVS framework. The core
  module has no direct full-calling, sending, receipt, recovery, main-persistence, or network
  dependency; the AVS module directly depends only on `:domain:calling-notifications`.
- Added flat Swift request, result, reason, summary, production-gate, cancellation, and completion
  surfaces. A per-run atomic gate provides at-most-once completion, including cancellation before
  coroutine body entry, and public DTO descriptions redact account, client, marker, App Group,
  payload, conversation, and user values.
- Adapted the Milestone 5 Apple process lock to the Milestone 4 bounded engine with cancellation
  checks before and immediately after acquisition. A composite idempotent lease closes registered
  lazy attempt resources before releasing the native process lock.
- Adapted the Milestone 6 handoff contract to the bounded-sync inbox. Raw events retain exact bytes,
  cursor and duplicate/conflict semantics are preserved, and complete child batches are durably
  staged before their parent is marked processed.
- Reused the Milestone 2 receive-only content boundary and Milestone 3 protobuf decoder/extractor.
  Decrypted protobuf bytes are copied unchanged into the handoff store; without a policy snapshot,
  details remain suppressed and every result requests a privacy-preserving fallback.
- Added a bounded notification-only AVS façade. The disposable Swift host reconstructs fresh AVS
  DTOs from copied scalar/string core values and invokes the native AVS façade synchronously while
  the process lock is held. No Kotlin object crosses between the two KMP framework images.
- Kept production construction fail-closed behind a stable scalar gate mask. Calling the factory
  performs no lock, storage, authentication, transport, CoreCrypto, or AVS access while any gate is
  unresolved.

Verification evidence:

- Both dynamic frameworks compiled and linked for iOS Simulator ARM64, iOS device ARM64, and macOS
  ARM64 with unified CoreCrypto and local provider-cache scope. The known native AVS debug-map and
  missing-debug-symbol warnings remain non-fatal.
- A Swift host compiled in application-extension mode against the regenerated framework headers and
  ran on the iPhone 16 Pro / iOS 18.4 simulator. It reported one completion, 32 immediate-expiration
  stress iterations, stage-before-ACK, lock-held storage access, store-close-before-unlock, exact
  protobuf retention, suppressed-content generic fallback, an unavailable production graph, and a
  synchronous core-to-Swift-to-AVS call while the lock remained held.
- The real notification-only native AVS façade was invoked with an intentionally malformed local
  synthetic payload and returned a safely classified native failure. This proves framework loading,
  scalar reconstruction, synchronous invocation, and return mapping; it is not real call-payload,
  policy, authentication, network, or CoreCrypto evidence. Exactly-one AVS close follows from the
  reviewed façade control flow rather than a native close counter.
- Dependency, generated-header, Mach-O, link, and symbol audits confirmed separate dynamically
  linked `MH_DYLIB`/two-level images, no AVS symbols in the core image, and no CoreCrypto/SQLCipher
  symbols in the AVS image. The public headers expose no `Either`, SQLDelight entity, CoreCrypto
  context, protobuf domain object, bounded-engine port, or AVS domain type.
- Repository `detekt`, `git diff --check`, untracked whitespace checks, and the no-test-file audit
  passed. Independent dependency and architecture/security reviews found no scoped blocker.
- No tests were added, modified, or run, per the spike instruction. Immediate cancellation evidence
  combines static cancel-before-launch review with a stress probe; it is not a deterministic
  scheduler proof.

Deferred production work:

- Implement the encrypted App Group handoff factory, shared Keychain authentication, validated
  entitlement-derived root, file-protection/locked-device behavior, real raw-frame transport
  capture, local-writer ACK guarantee, and structured server-timestamp mapping.
- Implement the raw-event-to-Proteus/MLS receive adapter, stable receive failure taxonomy, and safe
  CoreCrypto-to-handoff crash ordering. The core binary necessarily retains broad CoreCrypto UniFFI
  symbols and a transitive `:core:data` network-model/Ktor residual; size and symbol hardening remain.
- Add notification-policy snapshot ownership and real call metadata. Bound AVS payload and identifier
  UTF-8 sizes before production and retain the known broad native AVS media linkage as an M1 gate.
- Replace the disposable public synthetic probe before distribution. Wire the real Swift
  `UNNotificationContent` handler through its own at-most-once gate and absolute safety cutoff.
- Validate a signed `.appex` on physical devices with real account/message/call payloads, app-versus-
  NSE contention, locked-device access, deadline pressure, and memory limits. A combined optional
  common-metadata/host compile also remains blocked by the existing Milestone 3
  `:data:message-content` datetime/Okio classpath gap; all target Apple framework links pass.
- Foreground import into the main database remains Milestone 8. Sending, read/delivery receipts, and
  active MLS sending recovery remain out of scope.

## Milestone 8 — Foreground Importer Contract and Integration

Status: Completed on 2026-07-13 in `7a098a341c5b`

Delivered:

- Extended the versioned `:data:notification-inbox` contract with a one-complete-parent foreground
  snapshot, stable ingest/item ordering, exact child/parent/snapshot tokens, a fixed action mapping,
  bounded raw/protobuf payload ownership, and an exact post-main-commit mark operation.
- Captured one maximum ingest-sequence boundary per snapshot and excluded newer rows. Raw and child
  BLOB lengths are inspected before materialization and the complete parent is jointly bounded.
- Required all child-bearing parents to be receive-complete, all captured rows to be import-pending,
  complete child index continuity, exact parent/scope linkage, known idempotency namespaces, valid
  state combinations, and SHA-256 equality before data crosses the handoff boundary.
- Defined native actions for application upsert, already-applied crypto state, completion, terminal
  failure, and foreground recovery. Application rows missing a conversation or protocol timestamp
  schedule raw recovery rather than inventing identity or ordering.
- Added exact all-row compare-and-set marking in one handoff transaction. The native main database
  commits first; only then do children and their parent move to `IMPORTED`. Exact replay returns
  `AlreadyImported`, while partial state or any token/hash/identity/lifecycle mismatch fails closed.
- Closed raw replay ownership: processed raw moves to `COMPLETED`; queued raw moves to
  `DEFERRED_TO_APP` only after the app-owned main transaction durably copies its exact envelope,
  hash, format, scope, parent identity, and canonical parent token.
- Kept `DurableCursor` immutable during import. Foreground transition reads and validates cursor
  provenance from its exact non-transient source row while the same account lock remains held.
- Added a standalone Swift reference with Foundation, Darwin, SQLite3, and CryptoKit only. It reads
  the cross-language schema directly, recomputes canonical tokens, imports into a synthetic native
  database, exact-verifies ledger and effect replay, marks the handoff, and closes both databases
  before lock release without importing a Kalium framework.
- Documented that the disposable Swift lock helper proves digest/`flock` parity only. It validates
  the final entry but does not implement Milestone 5's descriptor-relative no-follow ancestor walk,
  so production native lock construction remains gated.

Verification evidence:

- `:data:notification-inbox` metadata, JVM, Android, iOS Simulator ARM64, and macOS ARM64 compilation
  passed. The macOS feasibility executable and iOS Simulator framework linked.
- macOS Swift compilation and iOS Simulator ARM64 application-extension type checking passed with
  warnings treated as errors.
- The KMP probe passed main-failure rollback, exact snapshot replay, late-row exclusion, post-commit
  mark, already-imported replay, immutable conflict, queued-raw no-replay, cursor immutability,
  complete-parent atomicity, and lock-coverage checks.
- The standalone Swift probe consumed an actual KMP-created Milestone 6 database and passed exact
  Kotlin/Swift snapshot-token parity, main-commit crash replay, stable exact side effects, conflict
  rejection, parent and protocol-timestamp ordering, durable raw ownership, cursor provenance and
  immutability, foreground transition under lock, and database-close-before-unlock checks.
- The resolved `:data:notification-inbox` common graph remains SQLDelight runtime/async, coroutines,
  Kotlin stdlib, and atomicfu only. No new dependency or module edge was added.
- Repository `detekt`, Swift no-Kalium-import inspection, `git diff --check`, untracked whitespace,
  and no-test-file audits passed. Independent mapping and transaction/security reviews found no
  remaining blocker inside the explicitly synthetic spike scope.
- No automated tests were added, modified, generated, or run.

Deferred production work:

- Integrate the importer with the real native application's protobuf mapper, message/effect tables,
  and app-owned exact import ledger. That application and database schema are outside this repo.
- Replace synthetic plaintext construction with encrypted App Group storage, shared Keychain keys,
  reviewed file protection, entitlement-derived roots, and the full Milestone 5 native no-follow
  path walk.
- Implement real Milestone 7 raw transport capture, receive mapping, and safe CoreCrypto-to-handoff
  crash ordering, including structured protocol timestamps and buffered MLS outputs.
- Define native `GlobalRecovery` read/ack ownership, legacy cursor seed/cutover/downgrade behavior,
  durable account-removal tombstones, and locked retention/cleanup with database-plus-sidecar bounds.
- Validate signed app-versus-NSE contention, physical and locked-device access, real backend/account
  payloads, crash injection, deadline pressure, storage pressure, and performance/memory limits.

## Milestone 9 — Resilience, Security, and Performance

Status: Completed on 2026-07-13 in `f7dff7073677`

Delivered:

- Evolved the handoff contract to version 2 with an explicit account lifecycle and two-phase cursor
  cutover. Preparation disables shared sync; exact activation is allowed only after the native app
  durably stops legacy sync. A crash can leave both paths disabled but never both authoritative.
- Added legacy-seed versus staged-raw cursor provenance, an atomic recovery-required transition, and
  bounded tokenized `GlobalRecovery` reads plus exact foreground acknowledgement. Recovery ACK does
  not reactivate synchronization, and downgrade remains fail-closed/foreground coordinated.
- Added permanent exact account-removal tombstones with one-transaction logical deletion of scoped
  child/cursor/raw/recovery rows, legacy-seed clearing, exact replay, mismatch conflict, and
  post-removal staging refusal. The tombstone database and stable lock entry must be retained;
  physical SQLite-page erasure or per-account key retirement remains a production decision.
- Added import-commit timestamps and bounded deterministic cleanup of only complete imported parents.
  Cleanup preserves the durable-cursor source, deletes children and parent transactionally, reports
  bounded continuation, never deletes recovery data, and never runs `VACUUM`.
- Added overflow-safe database/WAL/SHM/rollback-journal accounting and conservative write-admission
  arithmetic. The Apple implementation is a synthetic path-based measurer; production remains gated
  on descriptor-relative encrypted App Group construction and approved size/reserve values.
- Added hard count, identifier, byte, safety-margin, and effective-run-duration ceilings to bounded
  sync. Oversized events stop before durable stage and transport ACK, and summaries expose ingress/
  drain byte counts. Added UTF-8 payload and identifier bounds before native notification AVS.
- Kept production factory construction fail-closed with explicit new gates for storage enforcement,
  native cursor cutover, tombstones, recovery ACK ownership, and physical-device budget approval.
- Added disposable macOS/iOS Simulator lifecycle, cleanup, rollback, tombstone, storage, token-parity,
  and byte-budget probes plus the detailed evidence report at
  `docs/spikes/ios-nse-milestone-9-hardening.md`.

Verification evidence:

- Notification-inbox macOS, notification-sync metadata, and both core/AVS iOS Simulator source
  compilations passed; the macOS executable and iOS Simulator framework linked.
- The macOS hardening probe passed under the real account lock in 112–151 ms; the iPhone 16 Pro /
  iOS 18.4 simulator passed the same probe in 121.097 ms.
- Failure injection proved rollback after cleanup child deletion and after tombstone scoped deletion,
  with successful reopen evidence. The cursor anchor remained retained.
- The bounded-sync probe passed byte rejection before stage/ACK, hard caller ceilings, finite close,
  and release behavior in 2.795 ms.
- Swift compiled with warnings as errors for macOS and type-checked for an iOS Simulator app
  extension. The native importer consumed a Kotlin-created contract-v2 database, and the lifecycle
  token probe matched Kotlin recovery/tombstone vectors.
- Contract-v2 schema compatibility fails older state closed. No dependency, module edge, library, or
  CI change was introduced.
- No automated tests were added, modified, generated, or run.

Deferred production work:

- Native-app main-database integration, durable cutover receipt, feature flags, downgrade/recovery
  release coordination, and account-removal orchestration are outside this repository.
- Encrypted App Group storage, shared Keychain, file protection, signed entitlements, backend/APNs,
  real crypto receive/crash ordering, and physical/locked-device evidence remain open.
- Physical secure-erasure or per-account encryption-key retirement for logically deleted inbox rows
  remains open.
- The byte/deadline/storage values are candidate spike ceilings. Physical-device RSS/jetsam, backlog,
  storage-pressure, AVS, and deadline measurements still need iOS/product approval.
- Contract 1 to contract 2 migration/rollout remains an explicit release decision; this spike does
  not silently migrate or recreate incompatible state.

## Milestone 10 — Rollout and Observability

Status: Implementation and verification complete; awaiting commit

Delivered:

- Added a pure version-1 native-host rollout snapshot with an explicit revision, issue/expiry
  window, feature state, kill-switch state, evaluated cohort decision, and coarse stop reason. The
  unavailable default, unsupported/malformed/future/stale state, disabled feature, excluded cohort,
  and unreadable control all fail closed; a locally readable stop decision wins because it can only
  reduce behavior.
- Evaluated rollout before `NotificationExtensionRuntime.execute`, which is the sole gateway to the
  bounded engine and therefore to the account lock, handoff storage, authentication/transport,
  CoreCrypto, and synchronous AVS bridge. Denied paths return generic fallback with an empty summary
  and do not read, clean, delete, recreate, or mutate staged data or cursor state.
- Added one post-completion versioned observation with stable result enums plus bucketed monotonic
  duration, deadline margin, and counts. It contains no exact byte counts, payload/protobuf data,
  message/call text, tokens, paths, native/exception text, or account/user/conversation/message IDs.
- Restricted optional correlation to a lowercase canonical UUID-v4 and redacted it from string
  output. Shape validation is not a privacy guarantee: random non-PII generation/provenance remains
  a native privacy responsibility.
- Preserved at-most-once completion through the existing atomic gate. Completion is invoked before
  observation construction; observation construction, a throwing sink, and sink dispatch are
  isolated from the required completion path. Shape/cardinality are bounded to one record, while a
  non-blocking wall-clock sink and retention/export remain native responsibilities.
- Added explicit native ownership bits for approved message/call fallback, feature/kill sources,
  cohort evaluation, diagnostics retention/export, notification replacement identifiers, cursor
  cutover, downgrade/rollback, and rollout stop conditions. Production still reports every existing
  gate plus new rollout/privacy/release gates and returns no instance.
- Added the detailed evidence and ownership report at
  `docs/spikes/ios-nse-milestone-10-rollout-observability.md` and extended the disposable Apple/Swift
  framework probe without adding a telemetry library, database table, dependency, module edge, or
  CI change.

Verification evidence:

- Notification-extension common metadata, iOS Simulator ARM64, and macOS ARM64 source compilation
  passed. The core and unchanged split AVS iOS Simulator frameworks linked; known AVS debug-map
  warnings remain non-fatal.
- Swift application-extension type checking passed with warnings treated as errors against the
  regenerated framework headers.
- The iPhone 16 Pro / iOS 18.4 simulator reported `rolloutFailClosed=true`,
  `rolloutDeniedRuntimeCalls=0`, `rolloutEligibleRuntimeCalls=1`,
  `rolloutCompletionAtMostOnce=true`, `stagedDataPreservedOnDisable=true`,
  `observationPayloadExcluded=true`, and `observerFailureNonFatal=true`. The surrounding M7
  stage-before-ACK, lock coverage, resource-close order, exact proto, generic fallback, and split-AVS
  evidence remained green.
- The resolved common dependency graph is unchanged. Generated-header review found only the intended
  rollout, readiness, bucket, observer, and UUID-correlation fields; no internal evaluator/validator,
  exact-byte field, `Either`, SQLDelight entity, CoreCrypto context, bounded-engine port, or AVS
  domain type is public. Symbol review retained zero notification-AVS entry points in the core image
  and zero CoreCrypto/SQLCipher symbols in the AVS image.
- Repository `detekt`, `git diff --check`, no-sensitive-field/source logging, build/dependency-file,
  untracked whitespace, and no-test-file audits passed. Independent privacy/security/rollout review
  fixed completion-before-diagnostics ordering and narrowed correlation to UUID-v4, then found no
  remaining scoped blocker.
- No automated tests were added, modified, generated, or run.

Deferred production work:

- Native authentication, fetch/cache, rollback protection and cross-run monotonic revision for the
  rollout snapshot; deterministic cohort derivation; operational owners; refresh cadence; rollout
  percentages; and stop/re-enable thresholds.
- Product-approved generic message/call fallback, notification replacement identifiers, diagnostics
  privacy review, consent/access, retention, sampling/export, and a genuinely non-blocking sink.
- Native app import mapping, legacy-stop receipt, cursor cutover, contract migration, downgrade and
  recovery release coordination, account removal, and staged-data draining during rollback.
- Every previously open encrypted App Group/Keychain/file-protection, signed entitlement,
  APNs/backend, real receive/CoreCrypto/AVS, locked-device, physical-device, RSS/jetsam, deadline,
  storage-pressure, and product-budget gate.

## Next Action

Review and commit Milestone 10, then hand the explicit external ownership and physical-device gates
to the native iOS/product/security integration plan. Continue to avoid adding, modifying, or running
tests until the spike design has been verified end to end.
