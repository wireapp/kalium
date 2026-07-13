# iOS NSE Lightweight Kalium — Milestone 9 Hardening Evidence

Date: 2026-07-13

## Scope and decision

Milestone 9 closes the resilience, lifecycle, storage-accounting, and bounded-work gaps that can be
addressed inside Kalium without inventing a native application, production App Group/Keychain
configuration, backend account, or physical-device evidence. The handoff contract is now version 2.
An older contract-1 database fails compatibility validation; this spike does not silently migrate or
recreate it.

Production construction remains unavailable. The values measured here are synthetic host/simulator
evidence and candidate ceilings, not product-approved iOS memory, storage, latency, or deadline
budgets.

## Cursor authority and downgrade safety

Cursor cutover is deliberately two phase:

```text
no account row
  -> CUTOVER_PREPARED       shared/NSE sync disabled
  -> SHARED_V1              only after native legacy sync is durably stopped
  -> GLOBAL_RECOVERY_REQUIRED
                             all cursor staging disabled; foreground owns recovery
  -> DISABLED_ACCOUNT_REMOVED
                             permanent tombstone; no reactivation
```

Preparation records the exact cutover ID, optional legacy cursor seed and its SHA-256, and activation
time. Exact replay is idempotent. A different seed, cutover ID, or activation identity conflicts.
Activation requires the same prepared cutover ID. Between preparation and activation, shared sync
remains disabled: a crash before the app's durable legacy-stop commit leaves only legacy sync
authoritative, while a crash after that commit leaves both paths disabled. The native app must
durably stop legacy synchronization before activation, so no crash point makes both cursors
authoritative.

`readCursor` exposes whether its value comes from `LEGACY_SEED` or a durably staged raw event. The
first non-transient raw event atomically replaces the seed with normal raw-row provenance.

There is intentionally no API that silently restores the legacy cursor and no implemented
downgrade API. A future downgrade path must first move authority to recovery-required and needs an
app-release-coordinated native decision. Global recovery acknowledgement does not reactivate
synchronization.

## Global recovery and account removal

Global recovery rows now carry a canonical SHA-256 token over a versioned length-framed tuple of
account, client, reason, and recorded time. Pending reads are stable and bounded. The native app
acknowledges the exact token only after its own transaction durably owns or completes the recovery
work. A repeated reason with different immutable data conflicts instead of being hidden by
`INSERT OR IGNORE`.

Account removal uses an exact permanent tombstone token over account, client, removal ID, reason,
and tombstone time. Under the same process lock, one transaction:

1. changes `AccountLifecycle` from `ACTIVE` to `TOMBSTONED`;
2. logically deletes children, the durable cursor, raw parents, and recovery rows in foreign-key-safe order;
3. clears any legacy seed and disables the cursor authority; and
4. commits the retained tombstone.

Exact replay returns the same token; different removal data conflicts. Staging after removal returns
`ACCOUNT_TOMBSTONED`. The database, account directory, and stable lock entry must be retained. A
future account/client identity reuse needs a separate generation contract; deleting this marker is
not reactivation.

This is query-visible logical deletion, not evidence that plaintext or protobuf bytes were
physically erased from SQLite free pages. Production account removal still needs an approved
secure-erasure or per-account key-retirement policy.

## Retention and physical storage bounds

Foreground import now records `imported_at_epoch_millis` on every parent and child in the same exact
post-main-commit handoff transaction. Imported time cannot precede receive time. Cleanup is a
bounded, deterministic foreground operation and is valid only while the account lock remains held.
It selects at most `maxParentRows + 1`, and deletes a parent only when:

- the parent and every child are imported;
- the parent's import time is at or before the retention cutoff;
- the complete child set fits configured structural limits; and
- the parent is not the current durable-cursor source.

Children and parent delete in one transaction. The synthetic failure point after child deletion
proved rollback and reopen recovery. Cleanup never deletes import-pending data or `GlobalRecovery`
rows, never removes the cursor anchor, and never runs `VACUUM`; freed pages remain available for
reuse. A raw row already transferred to durable foreground ownership can remain classified as
deferred/recovery-required and still become cleanup-eligible after the retention cutoff.

`NotificationInboxStorageFootprint` counts the main SQLite file, WAL, shared-memory sidecar, and
rollback journal separately with overflow-safe arithmetic. Admission includes a caller-supplied
worst-case write reserve for SQLite pages, encryption, and journaling. The Apple synthetic measurer
rejects non-regular, foreign-owned, multi-link, or group/other-writable entries. It is path-based and
synthetic only; the production factory must measure fixed names descriptor-relatively beneath the
validated App Group root and enforce the approved cap before any write that could precede transport
ACK.

The closed DELETE-journal synthetic fixture occupied 98,304 bytes; WAL, SHM, and journal were absent
and therefore accounted as zero. This is an arithmetic and file-validation proof, not a production
SQLCipher size forecast.

## Bounded sync, memory, and AVS inputs

The bounded engine now rejects unbounded caller inputs before lock acquisition. Candidate defaults:

| Budget | Default | Absolute spike ceiling |
| --- | ---: | ---: |
| Transport frames | 200 | 1,000 |
| Events staged | 100 | 500 |
| Drain batches | 4 | 16 |
| Events per drain batch | 25 | 128 |
| Raw envelope | 256 KiB | 1 MiB |
| Raw ingress per run | 4 MiB | 16 MiB |
| Raw drain bytes per run | 4 MiB | 16 MiB |
| Safety margin | 2 s | 5 s |
| Effective run duration | 20 s | 30 s |

The effective deadline is the earlier of the host deadline and `start + maxRunDuration`. An event
that exceeds the per-event or cumulative byte budget is rejected before durable staging and before
transport ACK. Summary counters expose ingress and drain raw-byte counts. The AVS façade caps a
batch at 32 events, 256 KiB per UTF-8 payload, 1 MiB total payload, and 4,096 UTF-8 bytes per
identifier before native AVS creation.

These byte budgets constrain dominant payload copies; they are not an RSS or jetsam guarantee.
CoreCrypto, Kotlin runtime, SQLite, and AVS native allocations still require signed physical-device
measurement and product approval.

## Crash and failure evidence

Disposable probe sources remain in sample/spike source sets, never test source sets.

| Failure boundary | Observed safe state |
| --- | --- |
| Crash after cutover preparation | Cursor read/staging rejected until exact activation |
| Global recovery recorded | Cursor staging rejected before and after exact foreground ACK |
| Cleanup after child delete, before parent delete | Entire delete transaction rolled back; reopen cleanup succeeded |
| Tombstone after scoped deletes, before commit | Lifecycle and all scoped rows rolled back; exact raw replay succeeded after reopen |
| Tombstone committed | Exact replay succeeded; mismatch conflicted; new staging rejected |
| Storage sum/reserve overflow | Invalid measurement; admission failed closed |
| Oversized sync event | Returned byte-budget exhaustion before stage or ACK |

Canonical Kotlin/Swift parity vectors from the synthetic scope are:

- recovery token: `3e59d1d316bdea4f7e3e965949218bf0d2dedb60731c338599f89d9ad99ea162`
- tombstone token: `9d5c188b8abeef4ad92f79ed781860dd0b79dbc4c418325c4cd4c37a45a65960`

## Verification evidence

- Notification-inbox macOS compilation, notification-sync metadata compilation, and both core/AVS
  iOS Simulator source compilations passed.
- The macOS hardening executable passed under the real Milestone 5 lock in 112–151 ms across the
  recorded runs.
- The iPhone 16 Pro / iOS 18.4 simulator host passed the complete hardening probe in 121.097 ms.
- The bounded-sync host probe passed in 2.795 ms, including byte rejection before stage/ACK and hard
  request ceilings.
- Swift compiled with warnings as errors for macOS and type-checked in application-extension mode
  for iOS Simulator ARM64.
- The standalone native importer consumed a fresh Kotlin-created contract-v2 database and retained
  its Milestone 8 crash-replay, token, ordering, raw ownership, and cursor guarantees.
- The separate Swift lifecycle-token probe matched both Kotlin vectors.
- Simulator debug Mach-O sizes were 51,646,040 bytes for the broad feasibility framework,
  48,474,184 bytes for the core NSE framework, and 13,721,728 bytes for the AVS framework. These are
  debug artifacts, not App Store thinned size or a production approval.
- No automated tests were added, modified, generated, or run.

## Production gates still open

- Real native-app database mapping, import ledger, feature flag, cutover receipt, downgrade release
  coordination, recovery ownership, and account-removal orchestration.
- Encrypted SQLCipher handoff storage, independent keys, shared Keychain access group, approved
  accessibility/file-protection classes, and descriptor-relative App Group construction/measurement.
- Physical secure-erasure or per-account encryption-key retirement for logically deleted inbox rows.
- Signed app/NSE entitlements, locked/first-unlock access, physical-device `.appex`, APNs/backend
  account, consumable WebSocket, exact raw capture, and local-writer ACK evidence.
- Real Proteus/MLS/Welcome/buffered-output receive mapping and CoreCrypto-to-handoff crash ordering.
- Physical-device cold start, peak RSS/jetsam, storage pressure, backlog, deadline margin, and
  notification-only AVS measurements with product-approved budgets.
- A migration/release decision from contract 1 to contract 2. The spike currently fails old state
  closed rather than interpreting or deleting it.
