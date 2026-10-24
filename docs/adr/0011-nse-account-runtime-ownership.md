# 11. Account Runtime Ownership and Receiving Replay

Date: 2026-09-22

## Status

Accepted

## Context

On Apple platforms the app, the Notification Service Extension (NSE) and the share extension are
separate processes that use the same account stores in the App Group: SQLite, CoreCrypto and the
shared Keychain. Two processes must never use one account's native resources at the same time.

The NSE runs for seconds under tight memory limits. It cannot log in, register a client or migrate
stores, and it must leave the app's event queue in a state the app can continue from. SQL and
CoreCrypto are separate stores, so a notification's effects cannot commit atomically across both.

## Decision

### Account ownership

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

### Preparation and lifetime

The app awaits `open`; synchronous getters return only active cached sessions.
Registration and restore invalidate readiness. A terminal account event found by the
NSE also writes a per-client invalidation marker that keeps readiness unreadable
until a different client publishes it. Successful preparation verifies the
client, keys, crypto stores and supported configuration before publishing a
versioned readiness sidecar. That sidecar permits the NSE to open a prepared
account without opening/migrating a global database. Tokens and keys stay in the
shared Keychain configuration.

App opening captures compatible readiness before revoking it. Ordinary release
may refresh that authorization after workers drain; it cannot certify interrupted
registration or restore. Logout serializes mutation and cleanup against the
originating session so stale work cannot reach a reopened account. Hosts must
quiesce their own account operations and observers before releasing ownership.
Background release keeps the app session but first stops sync and closes its
WebSocket client, while no reopen can interleave; sync restarts only after the
app owns the account again. Legacy push skips clients the backend serves over
WebSocket, so an open socket would hide new events from the NSE. The native
socket and the backend see the close asynchronously, so events in that short
window may still reach the closing socket instead of APNs.

NSE opening retries a busy lease for up to 3 seconds, which covers the app's background
handoff. If the account stays busy, the NSE asks the host for a generic alert unless the host
reports that the foreground app presents alerts or another attempt in the same process holds
the account. Missing or incompatible readiness, such as after an app update until the app opens,
also asks for one unless the app presents alerts, and so does storage it cannot read. Opening
then reads compatible readiness,
checks existing store files and credentials, and opens owned resources. It never
logs in, registers a client or initializes missing stores to repair preparation.
SQLite opens without creation or migration. For an encrypted account, the NSE
reads the app's existing raw database key (on iOS with its salt) from the shared
Keychain; both the read-only schema probe and every pooled connection are keyed
before reading pages. Missing or invalid keys never trigger generation. The iOS
32-byte readable header and WAL keep shared databases recognizable during
suspension; macOS uses SQLCipher's default encrypted header. CoreCrypto's API
still opens or creates/migrates its store, so readiness is a practical guard, not
a stricter native contract.

Cancellation stops new work and drains resource users before network, crypto and
SQLite close. Unlock only after successful cleanup. A failed native free may mark
its handle destroyed before releasing the resource, so a later no-op close is not
proof of cleanup. Preserve that failure and retain the runtime/lease until process
exit. App live calling teardown first cancels and joins Kotlin callback work, then
destroys the AVS handle and waits for its native shutdown callback before disposing
the callback reference and releasing account ownership. A cancelled or incomplete
shutdown retains the handle for a later close attempt. An exception during native
creation leaves ownership uncertain and retains the account lease until process
exit. The NSE event-only AVS API instead retains one account/client handle and
callback context for the process lifetime. Its request callbacks are attached only
for the serialized start/process/end batch; this retained context owns no database,
crypto, network resource or account lease. These rules prevent simultaneous native
access and use-after-close.

### Queue and transactions

The app and NSE share `Events` in insertion order. Convert app live rows to pending
before NSE processing. Store each complete REST page and its fetch cursor in one
SQLite transaction; only the last non-transient event advances the existing app
cursor. Resume already stored pending work before fetching more. No separate queue,
REST ordering table or notification acknowledgement store is added.

The app processes `Events` strictly in order. The NSE may defer some message-only
notifications and process later independent ones, so it can acknowledge rows out
of order; see [conversation isolation and deferral](#conversation-isolation-and-deferral).
Any other failure fails the attempt.

For each notification, the NSE uses the app's Proteus-then-MLS nesting order:

1. Decode payloads in backend order inside the owned native transactions.
2. Apply required effects and pending delivery confirmations in one writer-bound
   SQLite transaction. Snapshot existing canonical messages before ordered writes
   when later clear/migration effects need them during replay.
3. Commit native crypto, then mark the existing event processed.

Required failures throw out of the native callback; returning an error value would
commit CoreCrypto. Restore the attempt's cancellation context inside its native
`NonCancellable` wrapper, and await unwind before releasing resources. NSE attempts
rethrow a callback error only after CoreCrypto confirms the abort; native abort and
commit errors take precedence. The app retains its gathered-batch boundary; it does
not adopt the NSE executor.

These are separate stores. SQL can commit before native crypto, and MLS can commit
before Proteus. Retry decrypts again, reapplies required fields and uses canonical
message IDs/native duplicate handling. MLS payloads covered by a recovery receipt
are not decrypted again; covered Proteus payloads are decrypted to advance the
session, but their effects are skipped. A stored system message acknowledges only
its own content, not the notification or every adjacent write. Reset,
recreate-after-wipe and deleted-peer guards remain where losing metadata or a native
group would destroy replay inputs.

There are no notification receipts, general outcome journals or startup recovery
mode. Besides the call signal journal and the account invalidation marker, narrow
durable records exist only where replay would otherwise lose inputs:

- MLS wrong-epoch recovery writes an intent before its external-join POST and payload
  receipts afterwards, in account Metadata; completion is recorded in the native
  CoreCrypto checkpoint inside the same native transaction as the join. These
  records load lazily and retire in the same SQL transaction as the processed marker.
  A join response that unexpectedly contains events is saved and refuses later NSE
  rejoins; nothing clears it.
- MLS conversation deletion keeps the group identity in a deletion intent, retired
  with the processed marker, so retry can wipe after SQL already deleted the
  conversation. Local MLS removal queues a wipe that is acknowledged after native commit.
- A conversation clear stores the exact deleted message IDs, so replay never deletes
  later messages. These snapshots are not pruned.

The NSE requires the prepared current schema version and never migrates SQLite.

### Conversation isolation and deferral

Only the Apple NSE enables this scan. It walks every pending row in insertion order.
A notification is isolatable when every payload is a Proteus or MLS new message in a
known, non-self conversation. Its dependencies are those conversations, the Proteus
sender sessions and the MLS groups.

An isolatable notification is deferred when, before persistence, it fails with no
network, a federation failure, a 5xx response, or MLS `WrongEpoch`,
`UnmergedPendingGroup`, `CommitForMissingProposal`, `ConversationNotFound` or
`OrphanWelcome`, and every payload it decoded before the failure can cross a gap.
Otherwise the failure fails the attempt. On deferral the native transactions roll
back, a 30-second retry hint is stored in Metadata (`nse-deferred-v1:<eventId>`,
removed on acknowledgement) and the scan continues. Later notifications that share
a dependency with a deferred one are skipped. `check`/`error` failures, SQL errors
and native commit errors never defer.

After a deferral, a later notification can be processed only when every decoded
payload can cross the gap: Text, Knock, Location, Asset, Composite, Poll, Multipart,
Ignored and Unknown content, or Creation, System, Profiles, DecryptionFailure,
Proposal and ClearProposalTimer payloads. Any other payload, or a non-isolatable
notification, is an ordering barrier and stops the scan. Results stay `SUCCESS`
and silent:

- `DEFERRED_CONVERSATION`: the target was deferred or skipped.
- `DEFERRED_ORDERING_BARRIER`: the scan stopped at a barrier, or the target was
  already processed while other rows remain pending.
- `DEFERRED_PRESENTATION_POLICY`: the target was processed after a deferral. Alert
  fields are cleared, AVS is not evaluated and only `callSignalsPending` is set.

Encrypted remote mute and in-call emoji are NSE no-ops but still pass through common
decryption and replay handling, and count as conversation dependencies. Typing is
skipped at DTO dispatch and adds no dependency.

### Bounded incremental sync

For every push the NSE runs the app's legacy catch-up protocol
(`EventIngestion`) before processing, like the Android app's one-shot sync. It opens
the WebSocket, stores the pending REST pages fetched once it is open, then closes
and joins the socket at live, so the socket is never open while the NSE processes.
Pages are fetched only after the socket opens, so no event falls between a page and
the socket. As in the app, catching up needs the socket: if it fails or does not
reach live within the catch-up budget, the catch-up fails, and nothing is paged
without it. Catch-up gets 60% of the attempt's budget; the isolated scan
then processes the stored queue to its end, without fetching, and stops before the
next event at 85%. Barriers and app handoffs stop the scan. Calls are evaluated only
with a complete prefix. Unread messages use the unnotified-message rules below. When
catch-up cannot finish, or fails or stops processing before the target with nothing
to show, the result asks the host for a generic alert: unseen events may remain.
There is no REST-only receiving mode.

A client registered for consumable notifications has no REST stream to page. Its
catch-up consumes its own session: each event is stored before it is acknowledged,
on that open session only, and the session is live when the backend confirms its
marker. Events after the marker stay unacknowledged and are delivered again. A
storage or acknowledgement failure ends catch-up rather than skipping the event. A
missed-notification signal stops catch-up without acknowledging it, so the app's
next connection receives it and recovers with slow sync; the host shows a generic
alert. No Kalium caller registers this capability yet.

### Proportionate replay guarantees

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
those signals. Unknown event types, property keys/values and feature settings follow
the app's no-op policy. Required undecodable work retains an explicit unsupported
boundary.

Preview metadata and quotes are durable. Optional image downloads run within the
owned attempt, validate encryption inputs/hash/CBC and cache successful files.
Download/image failure leaves text usable; cancellation still unwinds the attempt.
Unreferenced temporary files are cleaned, and required deletion keeps stored paths
until file cleanup succeeds. No preview queue or detached worker is added.

### App handoffs and presentation

Use existing durable app consumers: pending deliveries, proposal timers/native
pending proposals, expiration metadata, asset/Cells records, client-change rows
and configuration notification flags. The NSE sends no optional proposal commit,
starts no expiration timer and creates no replacement consumer. Native proposal
responses must be consumed by the app's existing scheduler.

When the app must run the effect itself, the NSE stops, returns `SUCCESS` with
`appWorkPending` and leaves the event and later queue in `Events`. The app's
incremental sync runs the matching consumer before gathering:

- `ACCOUNT_INVALIDATION`: a stored pending row removes this client or deletes the
  account. The NSE checks this before processing any row, so earlier rows stay
  pending too. It writes the invalidation marker and revokes readiness without
  deleting stores; the app runs the same offline scan and logs out with the
  matching reason instead of processing the queue.
- `LEGAL_HOLD`: app legal-hold transitions are pending, or a membership change or
  fetched profile disagrees with the stored legal-hold status. The app recovers its
  transition journals, then processes the events normally.

The Apple app's pre-sync cleanup also retries pending MLS group wipes queued by local
removal.

Presentation is selected from final canonical state. Actions/system messages alone
are silent. Read/mute/archive/availability, verification, expiration and host privacy
settings determine alert/body eligibility.

An attempt can process messages whose own pushes never present them: an earlier push
found the account busy or ran out of time, APNs kept only the latest push while the
device was offline, or pushes arrived out of order. Like the Android app, the NSE
selects these from existing unread state rather than its own record: unread messages
newer than their conversation's `last_notified_date` (`Notification.sq`), rechecked
with the receiving eligibility rules. Only an attempt that processed every event
through its target, without deferral or app handoff, presents them; a later complete
attempt presents what a deferral held back. The request keeps its own alert, or shows
the newest unnotified message when it has none, and returns at most ten others for
the host to post. The NSE then advances each candidate conversation's date, so
interruption before the host posts can lose those alerts but a later attempt cannot
repeat them. Messages older than six hours are marked without an alert.

While it runs, the app maintains the same date as the Android app does: it marks each
conversation notified through the newest message it presented, and marks the open
conversation even when it shows no alert. Otherwise the NSE alerts unread messages the
app received; the age bound limits those, such as after an upgrade. The NSE registers
alerted messages and returns delivered identifiers whose message is now deleted or
read, for the host to remove; updating delivered alerts remains optional. The host
owns the single system completion callback.

### Incoming calls

The NSE collects committed calling signals through the target, evaluates one AVS
event-only batch after sync and returns its callbacks for the host to report to
CallKit. Committed signals are retained in the account's call signal journal for
replay through the full app engine, which owns ringing and termination. Besides
storing committed signals, the NSE writes only a once-per-signal missed-call claim.
AVS exposes no message or session identity, so identical callback references cannot
distinguish separate real calls. As with ordinary alerts, interruption after
processing can lose presentation; exactly-once reporting is not claimed.

## Consequences

The NSE receives a bounded, known set of event families; the
[NSE README](../../nse/README.md#what-the-nse-receives) lists them with the
dependencies that keep the rest pending. Destructive MLS replay cannot be enabled by
discarding its required effects.

Known gaps follow from the decisions above:

- The backend sends no push for events it delivers over the open socket. A frame
  the socket delivered but had not stored when it closed, such as one that arrived
  while pages were fetched, is not alerted; the next sync's paging stores it. A REST
  sweep after closing would close the gap; it is left out to match Android until the
  gap shows up in practice.
- An external commit can succeed remotely before local failure. Only wrong-epoch
  recovery has a durable intent; other establishment commits need the returned
  events or welcome for replay, and legacy teammate creation needs an idempotent
  backend operation or authenticated reconciliation.
- CoreCrypto reports malformed MLS input and provider/store failures as the same
  `Other` error, so the NSE cannot turn either into a failed-message row.
- Saving a CRL does not replace earlier in-memory sources or clear native
  certificate-status caches. An already-used environment can keep a VALID status; a
  fresh environment reads the saved revocation.
- Simulator tests do not cover APNs delivery, the real extension lifecycle, shared
  protected Keychain access or signed-device budgets.
