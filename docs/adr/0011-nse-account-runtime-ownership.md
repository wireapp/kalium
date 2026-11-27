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

An extension holding the lease can be suspended with its host, so the app's wait for
it stays cancellable and gives up after 15 seconds with `AccountBusyException`; the
app keeps what it shows and retries. The holder writes its process name, pid, time and
whether it is an app extension into the lock file body. Locking never reads it; the
app logs it when it gives up waiting, and extensions use it to decide how long to wait.

iOS kills a suspended app that still holds a lock, so the app takes an account's lock
only while it has execution time: in the foreground, or while an execution hold keeps
that account (a call, a CallKit action, a background upload, initial sync or backup
restore). Kalium enforces this where it takes the lock. The background release pauses
every account no hold keeps and logs each account still locked with its hold reasons,
or as unavailable when its release failed, so the host can end the app instead.

The ownership primitive lives in `:domain:messaging:receiving`; the Swift facade lives
in the iOS-only `:nse` module. There is no generic opener bundle or common runtime coordinator.

### Preparation and lifetime

Readiness is durable account state. The app revokes it before registration,
credential replacement, logout and session removal, and publishes it only after
verified preparation: after registration and whenever its sync reaches live.
Opening, resuming and releasing an account, or the app dying, leave it unchanged. A
terminal account event found by the NSE writes a per-client invalidation marker that
keeps readiness unreadable until a different client publishes it. The versioned
readiness sidecar lets the NSE open a prepared account without opening or migrating
a global database. The NSE refuses a changed client or MLS registration, an unusable
cipher suite and incompatible stores, and uses the server configuration of the last
preparation.

Background release keeps the app session but first stops sync and closes its
WebSocket, because legacy push skips clients the backend serves over WebSocket and
an open socket would hide new events from the NSE. Events in the short window while
the socket closes may still reach it instead of APNs. If the release still waits
after 1 second, it fails the account's HTTP requests as offline, because iOS
suspends the app about 4.8 seconds after it enters the background. A failed MLS
commit request counts as rejected, so CoreCrypto drops the commit even if the backend
accepted it; the client then lags the group until its next incoming message or send
rejoins it, the same as when the process dies during that request.

NSE opening retries a busy lease for up to 3 seconds, which covers the app's background
handoff, and waits for another attempt of the same process to finish: that attempt presents
only what it received. If the account stays busy, or readiness is missing or incompatible,
or storage cannot be read, the NSE asks the host for a generic alert. It never logs in,
registers a client or initializes missing stores; SQLite opens without creation or
migration, and missing or invalid database keys never trigger generation. CoreCrypto's
API still opens or creates/migrates its store, so readiness is a practical guard, not
a stricter native contract.

Cancellation drains resource users before network, crypto and SQLite close, and
the lease is released only after successful cleanup. A failed or uncertain native
close retains the runtime and lease until process exit; an extension in that state
ends its process once it has delivered its result. The NSE's event-only AVS handle
is retained for the process lifetime and owns no account resource or lease.

### Queue and transactions

The app and NSE share `Events` in insertion order. Store each complete REST page and
its fetch cursor in one SQLite transaction; only the last non-transient event advances
the existing app cursor. No separate queue, REST ordering table or notification
acknowledgement store is added.

The app processes `Events` strictly in order with its standard batch processor: a
failed batch keeps its native crypto changes and is retried without acknowledgement.
The NSE may defer some message-only notifications and process later independent ones,
so it can acknowledge rows out of order; see
[conversation isolation and deferral](#conversation-isolation-and-deferral). Any other
failure fails the attempt.

For each notification, the NSE uses the app's Proteus-then-MLS nesting order:

1. Decode payloads in backend order inside the owned native transactions.
2. Apply required effects and pending delivery confirmations in one writer-bound
   SQLite transaction.
3. Commit native crypto, then mark the existing event processed.

Required failures throw out of the native callback; returning an error value would
commit CoreCrypto. NSE attempts rethrow a callback error only after CoreCrypto
confirms the abort.

These are separate stores. SQL can commit before native crypto, and MLS can commit
before Proteus. Retry decrypts again, reapplies required fields and uses canonical
message IDs and native duplicate handling. Reset, recreate-after-wipe and deleted-peer
guards remain where losing metadata or a native group would destroy replay inputs.
The NSE rejoins an MLS group by external commit only when CoreCrypto lacks it or is
behind the remote epoch, so every interruption around the rejoin replays without
stored intents or receipts.

Besides the call signal journal and the account invalidation marker, narrow durable
records exist only where replay would otherwise lose inputs:

- Deleting an MLS conversation queues its group wipe in the same SQL transaction;
  the wipe is acknowledged after the native commit.
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
a dependency with a deferred one are skipped.

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

### Bounded incremental sync

For every push the NSE runs the app's legacy catch-up protocol
(`EventIngestion`) before processing, like the Android app's one-shot sync. It opens
the WebSocket, stores the pending REST pages fetched once it is open, then closes
and joins the socket at live, so the socket is never open while the NSE processes.
Pages are fetched only after the socket opens, so no event falls between a page and
the socket. Without a live socket nothing is paged; there is no REST-only receiving
mode. Catch-up gets 60% of the attempt's budget; the scan then processes the stored
queue to its end and stops before the next event at 85%. When catch-up cannot finish,
or fails or stops processing before the target with nothing to show, the result asks
the host for a generic alert: unseen events may remain.

A client registered for consumable notifications catches up on its own session: each
event is stored before it is acknowledged, and a missed-notification signal stops
catch-up without acknowledging it, so the app's next connection recovers with slow
sync. No Kalium caller registers this capability yet.

### Proportionate replay guarantees

Required permission/member writes, sender authorization, crypto integrity and
physical cleanup remain enforced. Exact access-change, promotion and connection-
warning history is best effort: retry may add an extra system message. This does
not justify blocking the entire notification and its tail. No prior-state journal
or extra fetch is added solely to reproduce a first attempt's snapshot.

Typing, remote mute and in-call reactions are transient app UI/call state; the owned
NSE ignores them. Unknown event types, property keys/values and feature settings follow
the app's no-op policy. Optional preview image downloads run within the owned attempt;
their failure leaves text usable. No preview queue or detached worker is added.

### App handoffs and presentation

Use existing durable app consumers: pending deliveries, proposal timers/native
pending proposals, expiration metadata, asset/Cells records, client-change rows
and configuration notification flags. The NSE sends no optional proposal commit,
starts no expiration timer and creates no replacement consumer.

When the app must run the effect itself, the NSE stops, returns `SUCCESS` with
`appWorkPending` and leaves the event and later queue in `Events` for the app's
incremental sync. The [NSE README](../../nse/README.md#what-the-nse-receives) lists the
cases. `ACCOUNT_INVALIDATION` is checked before any row is processed: the NSE writes
the invalidation marker and revokes readiness without deleting stores, and the app
logs out instead of processing the queue. Work that stops every later attempt at the
same event, such as legal hold, makes a push whose event waits behind it ask for a
generic alert.

Presentation is selected from final canonical state. Actions/system messages alone
are silent. Read/mute/archive/availability, verification, expiration and host privacy
settings determine alert/body eligibility.

An attempt can process messages whose own pushes never present them: an earlier push
found the account busy or ran out of time, APNs kept only the latest push, or pushes
arrived out of order. Like the Android app, the NSE selects these from existing unread
state rather than its own record: unread messages newer than their conversation's
`last_notified_date`, rechecked with the receiving eligibility rules. Only an attempt
that processed every event through its target, without deferral or app handoff,
claims them, at most ten at a time; messages older than six hours are marked without
an alert. A claim advances the conversation's date and records the alerts as pending.
They are posted through the host before the account is released and stay pending
until the host accepts them, so a later owner presents what an interrupted one did not.
While it runs, the app marks each conversation notified through the newest message it
presented, as the Android app does.

### Incoming calls

The NSE collects committed calling signals through the target, evaluates one AVS
event-only batch after sync and returns its callbacks for the host to report to
CallKit. Committed signals are retained in the account's call signal journal for
replay through the full app engine, which owns ringing and termination. They are
kept for three minutes, because a new full engine ignores anything older: AVS drops
conference starts older than 120 s and only reports a 1:1 start older than 60 s as
missed. When the full engine closes a call that does not continue for others, the
signals it handled in that conversation before the close callback are dropped, so a
restart cannot replay an ended call. Besides storing committed signals, the NSE writes
only a once-per-signal missed-call claim. Exactly-once reporting is not claimed.

## Consequences

The NSE receives a bounded, known set of event families; the
[NSE README](../../nse/README.md#what-the-nse-receives) lists them with the
dependencies that keep the rest pending. Destructive MLS replay cannot be enabled by
discarding its required effects.

Known gaps follow from the decisions above:

- The backend sends no push for events it delivers over the open socket. A frame
  the socket delivered but had not stored when it closed is not alerted; the next
  sync's paging stores it. A REST sweep after closing would close the gap; it is left
  out to match Android until the gap shows up in practice.
- An external commit can succeed remotely before local failure. Establishment commits
  other than rejoining a group need the returned events or welcome for replay, and
  legacy teammate creation needs an idempotent backend operation or authenticated
  reconciliation.
- The NSE uses the server configuration, including the API version, of the app's last
  preparation. A version the app negotiates later reaches it when the app's sync is next
  live.
- CoreCrypto reports malformed MLS input and provider/store failures as the same
  `Other` error, so the NSE cannot turn either into a failed-message row.
- Saving a CRL does not replace earlier in-memory sources or clear native
  certificate-status caches. An already-used environment can keep a VALID status; a
  fresh environment reads the saved revocation.
- Simulator tests do not cover APNs delivery, the real extension lifecycle, shared
  protected Keychain access or signed-device budgets.
