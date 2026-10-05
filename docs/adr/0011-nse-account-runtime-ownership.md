# Account runtime ownership and receiving replay

## Decision

The Apple app and extensions share one exclusive per-account lease for the entire
lifetime of account database, network and crypto resources. The lock is a
persistent `flock` file outside the removable account directory, named using the
versioned hash of the qualified account identity. Never unlink it during logout:
other processes must keep locking the same inode. One blocked account must not
prevent unrelated accounts from closing.

The ownership primitive lives in `:domain:messaging:receiving`. Existing SQLite
opening stays in persistence, app session construction stays in logic, and the
Swift facade lives in the iOS-only `:nse` module. Shared mapping and business rules
remain below logic; app UI, live workers and platform integrations keep their
existing owners. There is no generic opener bundle or common runtime coordinator.

## Preparation and lifetime

The app awaits `open`; synchronous getters return only active cached sessions.
Registration and restore invalidate readiness. Successful preparation verifies the
client, keys, crypto stores and supported configuration before publishing a
versioned readiness sidecar. That sidecar permits the NSE to open a prepared
account without opening/migrating a global database. Tokens and keys stay in the
shared Keychain configuration.

App opening captures compatible readiness before revoking it. Ordinary release
may refresh that authorization after workers drain; it cannot certify interrupted
registration or restore. Logout serializes mutation and cleanup against the
originating session so stale work cannot reach a reopened account. Hosts must
quiesce their own account operations and observers before releasing ownership.

NSE opening acquires the lease without waiting, reads compatible readiness,
checks existing store files and credentials, and opens owned resources. It never
logs in, registers a client or initializes missing stores to repair preparation.
SQLite opens without creation or migration. CoreCrypto's available API still
opens or creates/migrates; readiness is a practical guard, not a stricter native
contract. [Native limitations](../NSE_IMPLEMENTATION_STATUS.md#native-contracts-that-constrain-receiving)
record this and the pinned artifact issues.

Cancellation stops new work and drains resource users before network, crypto and
SQLite close. Unlock only after successful cleanup. A failed native free may mark
its handle destroyed before releasing the resource, so a later no-op close is not
proof of cleanup. Preserve that failure and retain the runtime/lease until process
exit. App live calling teardown first cancels and joins Kotlin callback work, then
destroys the AVS handle and waits for its native shutdown callback before disposing
the callback reference and releasing account ownership. A cancelled or incomplete
shutdown retains the handle for a later close attempt. An exception during native
creation leaves ownership uncertain and retains the account lease until process
exit. The NSE event-only AVS API instead retains one account/client handle
and callback context for the process lifetime, matching the iOS NSE branch. Its
request callbacks are attached only for the serialized start/process/end batch;
this retained context owns no database, crypto, network resource or account lease. These rules prevent simultaneous native access and use-after-close.

## Queue and transactions

The app and NSE share `Events` in insertion order. Convert app live rows to pending
before NSE processing. Store each complete REST page and its fetch cursor in one
SQLite transaction; only the last non-transient event advances the existing app
cursor. Resume already stored pending work before fetching more. No separate queue,
REST ordering table or notification acknowledgement store is added.

For each notification, the NSE uses the app's Proteus-then-MLS nesting order:

1. Decode payloads in backend order inside the owned native transactions.
2. Apply required effects and pending delivery confirmations in one writer-bound
   SQLite transaction. Snapshot existing canonical messages before ordered writes
   when later clear/migration effects need them during replay.
3. Commit native crypto, then mark the existing event processed.

Required failures throw out of the native callback; returning an error value would
commit CoreCrypto. Restore the attempt's cancellation context inside its native
`NonCancellable` wrapper, and await unwind before releasing resources. The app
retains its gathered-batch boundary; it does not adopt the NSE executor.

These are separate stores. SQL can commit before native crypto, and MLS can commit
before Proteus. Retry decrypts again, reapplies required fields and uses canonical
message IDs/native duplicate handling. A stored system message acknowledges only
its own content, not the notification or every adjacent write. Deletion and reset
guards remain where losing metadata or a native group would destroy replay inputs.

There are no receiving checkpoints, NotificationReceipts, outcome journals or
startup recovery mode. Migrations 147 through 149 retain compatibility with databases
created during the earlier implementation and drop the former receipt table.
Removing those migrations requires a separate compatibility decision.

When upstream meeting migration 143 shifts local migrations to 144 through 149,
older fork databases at versions 144 through 149 still have `Meeting.trial` and
no `tzid`. App and backup migration inspect those columns, apply the unchanged
upstream conversion, and resume at the old version plus one. This skips local
operations the fork already completed. Upstream databases already containing
`tzid` use their original version. Explicit migration binds one writer connection
and commits the schema and version together, as automatic production opens do.
The NSE still requires the prepared current version and never migrates SQLite.

## Proportionate replay guarantees

Required permission/member writes, sender authorization, crypto integrity and
physical cleanup remain enforced. Exact access-change, promotion and connection-
warning history is best effort: after SQL commit/native abort, the original
no-change input may be gone and retry may add an extra system message. This does
not justify blocking the entire notification and its tail.

An access event before an unknown-conversation fetch initially has no row to
update. On retry it updates the now-existing row, following the app's direct field
flow; access may differ from the first attempt's fetched snapshot. No prior-state
journal or extra fetch is added solely to reproduce that snapshot.

Repeated text identities use existing canonical message/sender checks instead of
a blanket batch rejection. Typing, remote mute and in-call reactions are transient
app UI/call state; the owned NSE has no active call or live observers and ignores
those signals. Unknown property keys/values and feature settings follow the app's
no-op policy. Unknown event types and required undecodable work retain an explicit
unsupported boundary.

Preview metadata and quotes are durable. Optional image downloads run within the
owned attempt, validate encryption inputs/hash/CBC and cache successful files.
Download/image failure leaves text usable; cancellation still unwinds the attempt.
Unreferenced temporary files are cleaned, and required deletion keeps stored paths
until file cleanup succeeds. No preview queue or detached worker is added.

## App handoffs and presentation

Use existing durable app consumers: pending deliveries, proposal timers/native
pending proposals, expiration metadata, asset/Cells records, client-change rows
and configuration notification flags. The NSE sends no optional proposal commit,
starts no expiration timer and creates no replacement consumer. Native proposal
responses must be consumed by the app's existing scheduler.

Only the target notification returns presentation, selected from final canonical
state. Actions/system messages alone are silent. Read/mute/archive/availability,
verification, expiration and host privacy settings determine alert/body eligibility.
A processed target succeeds silently. An all-native-duplicate retry may miss an
alert; updating/removing delivered iOS alerts and missed-alert recovery remain
optional. The host owns the single system completion callback.

The [coverage table](../NSE_IMPLEMENTATION_STATUS.md#current-coverage) names remaining
native, backend and consumer dependencies. Terminal cleanup and destructive MLS replay cannot be enabled by discarding
their required effects. Incoming calling uses the iOS branch’s presentation flow:
collect committed signals through the target, evaluate one AVS batch after sync,
and return its callbacks to the Swift CallKit reporter. Committed signals are retained
in the existing account Call signal journal for replay through the full app engine.
Both AVS adapters use the journal's first ready start matching the incoming callback
reference, including caller client when supplied. Native generations do not advance
selection through tied starts. The NSE lookup is read-only; the full app engine owns
ringing and termination, and replay preserves terminal presentations. Identical callback
references cannot distinguish separate real calls because AVS exposes no message or
session identity. As with ordinary alerts, interruption after processing can lose
presentation; exactly-once reporting is not claimed. The host awaits reporting before completion.
