# iOS NSE Milestone 8 — Native Foreground Import Contract

Date: 2026-07-13

## Boundary

The new foreground iOS application does not consume Kalium or a KMP framework. The production
integration boundary is therefore the on-disk handoff schema, the Milestone 5 lock derivation, the
state strings, and the transaction protocol. SQLDelight generated types are not part of this API.

This repository can own and verify:

- the versioned `:data:notification-inbox` snapshot and exact-mark behavior;
- a standalone Swift reference implementation using Foundation, Darwin, SQLite3, and CryptoKit;
- a synthetic native main database that demonstrates the required transaction ordering.

It cannot integrate the real native application database because that project and schema are not
in this repository. The Swift prototype accepts an already opened handoff connection in its
production-shaped reader. Path-based plaintext construction is used only by the synthetic probe.

## Lock and lifetime

The foreground application derives the same lock path as Milestone 5:

```text
<app-group-root>/kalium-nse/v1/<account-client-sha256>/sync.lock
```

The digest input and published vector remain those in `AppleProcessLockFactory`. The native
foreground process may use a short product-approved bounded wait before acquisition; once acquired,
one descriptor-backed exclusive `flock` must remain held across:

1. handoff open and compatibility checks;
2. snapshot read;
3. the native main-database transaction;
4. the exact handoff mark transaction;
5. shared-cursor read and foreground-sync/CoreCrypto ownership transition; and
6. both database closes.

The disposable probe uses one non-blocking acquisition and proves a contender cannot acquire until
the operation closes. Its Swift helper is deliberately not production-equivalent path security: it
builds a string path, opens the final lock entry with `O_NOFOLLOW`, and validates that final file,
but it does not perform Milestone 5's descriptor-relative no-follow walk over every ancestor. It may
only be used by this synthetic plaintext probe. Production construction remains gated on the
hardened, entitlement-derived App Group root and the complete Milestone 5 path walk, and must not
replace the stable lock file or its parent tree.

## Snapshot protocol v1

One snapshot contains exactly one complete parent. A parent is never split between main-database
transactions. Processing order is:

```text
RawEvent.ingest_sequence ASC
    then ReceiveChild.item_index ASC
```

The reader captures `MAX(RawEvent.ingest_sequence)` and selects the earliest eligible parent at or
below that boundary. A `limit 2` lookahead provides `hasMore`. Events inserted later have a higher
AUTOINCREMENT sequence and cannot enter the captured snapshot. The snapshot is valid only while the
same account lock and database instance remain open; callers restart from the earliest pending
parent after release or process restart.

Each child includes `child_sequence`, parent sequence and event ID, item index, scoped idempotency
key, exact protobuf and SHA-256, protocol metadata, crypto-applied flag, receive/decryption/failure
states, notification state, retry count, and native action. Raw data is exposed only when the native
database must durably process or queue the raw event.

The action mapping is:

| Handoff state | Native action |
| --- | --- |
| Application message + decrypted protobuf + `crypto_state_applied=1` + conversation/timestamp identity | Upsert application data from the exact protobuf; never decrypt again |
| Otherwise valid application result missing conversation or timestamp identity | Durably schedule raw foreground recovery; do not invent message identity or ordering |
| Handshake-only + applied crypto state | Record completion/import ledger only; never apply the handshake again |
| Welcome, unsupported, unapplied handshake, deferred/recovery raw | Durably schedule foreground recovery |
| Non-message or zero-output completed raw | Record completion |
| Terminal failure | Record the terminal result without retrying crypto |

The main message identity is the qualified conversation ID plus `GenericMessage.messageId` decoded
from the exact protobuf. The handoff child idempotency key and complete canonical child record token
are also written to an app-owned import ledger. Raw ledger entries use the canonical parent record
token. These tokens bind parent/item ordering, identities, metadata, payload hash, and action; a
proto hash plus action alone is not an exact replay record. The ledger is mandatory for handshake
and signaling work that intentionally creates no message row. Same identity and exact token/effect
is an exact replay; same identity with different content or metadata is an integrity conflict. A
plain `INSERT OR IGNORE` is not sufficient.

Message/UI order uses `message_timestamp_epoch_millis`, with parent ingest sequence, item index, and
idempotency key as deterministic tie-breakers. Import time is never a message timestamp.

## Canonical tokens

The public protocol uses three lowercase SHA-256 values: child record, parent record, and snapshot.
All use a canonical frame. Each UTF-8 field is prefixed by a four-byte big-endian signed length;
`-1` represents null. The prefix itself is the first framed field. Integers are base-10 ASCII and
booleans are `0` or `1`.

Prefixes are:

```text
com.wire.kalium.notification-inbox.foreground-child/v1
com.wire.kalium.notification-inbox.foreground-parent/v1
com.wire.kalium.notification-inbox.foreground-snapshot/v1
```

The Kotlin and Swift implementations frame the same ordered fields. The snapshot binds protocol
version, scope, captured maximum sequence, initial position `0`, parent sequence/token, child count,
and every ordered child sequence/token. Payload bytes are verified against their stored SHA-256
before a token is accepted.

## Commit and crash protocol

For each complete parent:

1. Read and validate the snapshot under the account lock.
2. In one native main-database transaction, exact-upsert the message/effect rows and import ledger.
3. Commit the native main database.
4. In one handoff transaction, re-read and validate every parent/child identity, state, hash, and
   token before making any change.
5. Compare-and-set every exact child `PENDING -> IMPORTED`.
6. Compare-and-set the exact parent `PENDING -> IMPORTED` and apply its raw disposition.
7. Retain imported rows; deletion is not part of import.

Raw dispositions close a replay hazard:

- `PROCESSED_BY_FOREGROUND` changes a raw `receive_state=PENDING` to `COMPLETED` only after the main
  commit.
- `DURABLY_QUEUED_FOR_FOREGROUND` changes it to `DEFERRED_TO_APP`, sets foreground recovery and the
  bounded v1 queue reason, and records that the native database now owns processing. Before that
  handoff mark, the same native transaction must copy the exact raw envelope, hash, format, scope,
  parent sequence/event identity, and canonical parent token into app-owned deferred work. A marker
  without the raw payload does not transfer ownership.

An exact already-imported parent and all its exact children returns `AlreadyImported`. Mixed import
states, missing rows, a new/different child, or any identity/hash/lifecycle mismatch fails closed.
No update is attempted until the complete parent has validated; update-count failure aborts the
handoff transaction.

| Failure point | Required result |
| --- | --- |
| Before/during main transaction | Main transaction rolls back; handoff remains pending |
| After main commit, before handoff mark | Replay finds the exact app ledger entry, emits no duplicate side effect, then retries the mark |
| During handoff mark | Handoff transaction rolls back; main replay remains harmless |
| After handoff mark | Parent is complete; processing continues with the next ingest sequence |
| Main or handoff conflict | Stop; later signaling parents must not overtake the conflict |

## Cursor and lifecycle gates

Import SQL never writes `DurableCursor`. Cursor movement remains owned by atomic raw staging. After
the final import, foreground sync reads the same cursor and assumes sync/CoreCrypto ownership before
releasing the lock.

The following remain fail-closed production gates:

- The existing legacy `last_processed_event_id` can seed the shared cursor only once under the lock,
  but `DurableCursor` requires a source `RawEvent`; the migration record, feature cutover, rollback,
  and downgrade policy are not implemented here.
- `GlobalRecovery` has no native read/ack lifecycle. Import does not silently clear it.
- Account removal needs a locked durable tombstone so a stale NSE cannot recreate state. It must
  stop sync/CoreCrypto, close databases, and retain the lock file/tree.
- Cleanup needs a locked, bounded retention and total database-plus-sidecar size policy. Imported
  rows are never deleted by the import transaction.
- Older/newer schema or token versions fail compatibility checks. There is no automatic downgrade,
  database recreation, or destructive repair.
- Production still requires encrypted App Group storage, Keychain sharing, file protection,
  no-follow path construction, real native database mapping, and physical-device validation.
- M7 still lacks real raw transport capture, receive mapping, CoreCrypto/handoff crash ordering, and
  a structured server timestamp. Real message-order fidelity remains gated by those adapters.

## Disposable evidence

The KMP macOS probe uses the public store contract and verifies parent atomicity, exact replay and
conflict behavior, late-row exclusion, raw receive disposition, cursor immutability, and full lock
coverage.

The standalone Swift probe consumes an actual database produced by the KMP synthetic factory. It
has no Kalium import and independently executes direct schema reads, canonical token recomputation,
native main transactions, exact import-ledger/effect replay, durable raw-payload transfer, exact CAS
marking, and foreground-sync transition. Its native main database is plaintext and synthetic by
construction; it is evidence of transaction and cross-language parity, not a production persistence
or production path-security implementation.
