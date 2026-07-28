# iOS main-process and Kalium NSE production integration audit

Date: 2026-07-27

Status: implementation hardened; production construction remains fail-closed while the blockers
listed below are open.

## Intended production architecture

Kalium is the only notification-processing engine. The main application owns the normal Kalium
session, migrations, foreground sync, durable application state, and foreground side effects. The
Notification Service Extension owns one bounded, receive-only Kalium attempt using the same account
identity, App Group root, Keychain access group, CoreCrypto state, and encrypted handoff inbox.

There is no alternate NSE engine or mock production path. When the real engine cannot safely
complete, the extension may still return generic privacy-preserving notification content because
iOS requires the content handler to be completed; that is a presentation safety action, not a
second processing engine.

## Integration invariants

1. The main process and NSE never open mutable state for the same account concurrently.
2. The main process does not release its account lease until all Kalium account resources have
   closed.
3. The NSE acquires the account lease before opening account storage, network, CoreCrypto, or the
   encrypted handoff inbox.
4. Raw transport bytes are durably staged before transport acknowledgement.
5. The NSE is receive-only. It does not run foreground repair, logout, data removal, schedulers, or
   outbound MLS traffic.
6. Decrypted notification data does not cross the public Kotlin-to-Swift result boundary.
7. A teardown or construction rollback failure retains the process lease until process exit.
8. Production construction remains unavailable unless every code-owned and host-owned gate is
   proven.

## Resolved in this audit

| Area | Resolution |
| --- | --- |
| Main-process ownership | Added a shared account process-lock coordinator and serialized Kalium session coordinator. Cancellation and failed close paths retain ownership instead of unlocking uncertain state. |
| Main lifecycle | Startup, login handoff, background teardown, logout, and scope destruction now join observer and Kalium teardown work. |
| Sole engine | The NSE entry point uses `RealNotificationExtensionFactory`; production synthetic probes and plaintext inbox factories were removed. |
| NSE run ownership | A real extension instance is one-shot and completion is at most once. Expiration cancels the bounded run. |
| Constructor/teardown safety | Partially constructed account resources are rolled back in dependency-safe order. Cleanup failure maps to unsafe teardown and retains the account lock. |
| Database ownership | NSE global, user, network, socket, processing-scope, MLS, and Proteus resources have explicit close paths. |
| Raw event fidelity | API v9 retains the exact binary frame. The bridge extracts the exact `data.event` JSON value, excludes the delivery tag, preserves unknown fields, and rejects duplicate escaped or unescaped `data`/`event` keys. |
| Transport acknowledgement | Acknowledgement now has accepted, retryable, and terminal results; the main event repository no longer treats every acknowledgement as success. |
| Receive materialization | Child indexing follows emitted output order and decrypted child rows carry explicit foreground classification. |
| Handoff storage | The inbox is SQLCipher-encrypted, bounded, descriptor-relative/no-follow, owned by the effective user, inode-verified, `0700`/`0600`, and protected as complete-until-first-authentication. |
| Swift boundary | Public NSE results expose status, reason, bounded counters, and a generic privacy presentation decision only. |
| Artifact checks | Packaging validates framework slices, architectures, extension-unsafe imports, and stale generated artifacts before release. |

## Code-owned production blockers

These cannot be asserted by the host readiness mask and must keep production construction closed.

### 1. CoreCrypto and inbox crash ordering

Child rows can become visible before the surrounding CoreCrypto transaction is known committed. A
crash in that window can leave a row claiming that crypto state was applied when the transaction
rolled back. Add a post-CoreCrypto-commit visibility transition, recovery reconciliation, and crash
tests before satisfying `CORE_CRYPTO_HANDOFF_CRASH_ORDERING`.

### 2. Foreground importer and durable side-effect ledger

The exact-once importer contract and encrypted store exist, but the main application's production
committer and caller do not. The main user database needs a transactional ledger/outbox containing
snapshot token, parent token, child token, raw disposition, recovery state, cursor authority, and
account-removal state. Side effects must dispatch idempotently from that outbox.

This requires an approved `:logic` to `:data:notification-inbox` module dependency (or a narrower
approved facade), persistence migrations, a post-decryption committer, and main-session bootstrap
wiring.

### 3. Cursor ownership and recovery

Implement `LEGACY`, `DISABLED_PREPARED`, and `SHARED_ACTIVE` cursor authority states with atomic
prepare/disable/activate transitions. The main process must import the shared inbox before ordinary
network sync. Global recovery tokens need a durable main-process record and acknowledgement.

### 4. Account deletion

Account deletion must first commit a tombstone, stop both processes from reopening the account,
drain or explicitly discard the handoff state, and only then remove account resources. Until then,
`ACCOUNT_REMOVAL_TOMBSTONE` stays blocked.

### 5. AVS ordering and extension-safe binary

AVS notification work currently lacks a durable post-commit idempotent outbox, so rollback or crash
can repeat host-visible call effects. `POST_COMMIT_IDEMPOTENT_AVS_DISPATCH` stays blocked.

The current AVS KMP archive imports application-only UIKit APIs including
`UIApplication.sharedApplication`. It must be replaced with an extension-safe, notification-only
AVS artifact before `EXTENSION_SAFE_AVS_BINARY` can pass.

### 6. Local-writer acknowledgement proof

Ktor `trySend`, `flush`, and active-session checks improve error classification but do not provide a
definitive socket-writer fence across termination races. Keep `LOCAL_WRITER_ACK_GUARANTEE` blocked
until the transport exposes a provable writer acceptance primitive.

### 7. Pre-decode frame bound

The current per-event and per-run byte limits apply after the WebSocket frame has been received.
The transport still needs a frame-size limit before allocation/string conversion/DTO decoding so an
oversized frame cannot consume the NSE memory budget first.

### 8. Notification policy snapshot

No versioned, authenticated foreground-owned policy snapshot is available to the NSE. Until it is
wired, decrypted candidates must remain private and the NSE may emit only generic privacy content.
`NOTIFICATION_POLICY_SNAPSHOT` stays blocked.

### 9. Exception-atomic provider construction

Rollback covers resources returned by the user-storage and authenticated-network providers.
Provider constructors themselves must either guarantee exception atomicity or expose partial
ownership for cleanup. This must be proven with fault-injection tests before release.

## Host and release evidence still required

- Signed app and NSE entitlements for the same App Group and Keychain access group.
- A freshly regenerated Kalium main XCFramework and NSE XCFramework from the audited submodule
  revision; checked-in artifacts currently predate the new API.
- Signed physical-device validation with real APNs, locked-device Keychain access, owner death,
  expiration, cold start, memory pressure, real Proteus/MLS traffic, and real AVS callbacks.
- A measured NSE execution/memory budget and approved rollout stop conditions.
- Native ownership for versioned rollout control, cursor cutover/downgrade behavior, and
  privacy-safe diagnostics retention/export.

## Release rule

Do not make `RealNotificationExtensionFactory.createProduction` constructible by weakening,
externally overriding, or bypassing a code-owned gate. Close the implementation and evidence gaps,
regenerate the frameworks, run the signed release validator, and only then satisfy the
corresponding gates.
