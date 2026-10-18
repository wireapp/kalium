# Kalium NSE receiver

`KaliumNSEReceiver` is the Swift entry point for a prepared Apple account. The
library receives supported Proteus/MLS events, persists shared app state and
returns a notification decision. It is a development integration: real host/APNs,
entitlement and signed-device lifecycle validation remain separate work.

## Building the XCFramework

`assembleKaliumNSEDebugXCFramework` and `assembleKaliumNSEReleaseXCFramework`
include device ARM64 and simulator ARM64 by default. Pass
`-Pwire.ios.simulatorOnly=true` or `-Pwire.ios.deviceOnly=true` to assemble only
that platform and avoid linking the other slice. Both flags together are invalid.
The targets remain available for other Gradle tasks.

## Integrating with an iOS host

Prepare the account through the app before using the receiver. App and NSE must
share the same canonical App Group root, Keychain service/access group/accessibility,
account identity and compatible store versions. The app publishes readiness only
after completing registration and crypto preparation. The NSE does not repair
missing preparation by logging in, registering or creating replacement stores.

Construct `KaliumNSEConfiguration` with the shared storage settings, user agent and
certificate pins. Match these build switches to the app's `KaliumConfigs`:

| Setting | Default | Meaning |
| --- | --- | --- |
| `guestRoomLink` | `true` | Guest-link feature support |
| `selfDeletingMessages` | `true` | Self-deleting message feature support |
| `drivePermissionsEnabled` | `false` | Drive membership permissions |
| `linkPreviewEnabled` | `false` | Optional incoming preview-image download |
| `allowedFileExtensions` | `null` | Unrestricted file types; an empty list allows none |

For each push, create `KaliumNSERequest` with the qualified user identity, target
notification ID, an additional host `showMessageContent` restriction and a budget
between 1 and 25,000 milliseconds. Optionally pass this account's currently delivered
message alerts in `deliveredNotifications`. Call `process` from Swift async code. Like
the app, the NSE first catches up over the WebSocket: it opens it, stores the REST pages
fetched once it is open and closes it at live, then processes the stored queue to its
end. Without a live socket nothing is fetched, and a push whose event was not processed
asks for a generic alert ([ADR 11](../docs/adr/0011-nse-account-runtime-ownership.md#bounded-incremental-sync)). The
[Swift fixture](src/swiftTest/Receive.swift) shows the concrete constructor and call.
Swift cancellation reaches the owned attempt.

The host owns iOS's single completion callback and its generic failure fallback.
`SUCCESS` with `shouldNotify == false` is successful silent processing, not failure.
An eligible result can be a message, conversation deletion or connection request.
A null body can mean a generic alert or a privacy restriction. The facade filters
message text, names and typed labels by the host flag, saved account preference
and conversation/expiration state. The saved choice is read freshly through the
owned account runtime before presentation. Absence preserves existing preview
eligibility; Disabled or a storage/decoding failure hides message content. Enabled
cannot override another restriction. When preview is not allowed, `contentType`
is `MESSAGE`. Cancellation still cancels the attempt. Message preference failures
do not suppress or alter call presentation. `presentationReason` and `failureReason`
are content-free diagnostic codes. `appWorkPending` names work left in Events for
the app (`LEGAL_HOLD` or `ACCOUNT_INVALIDATION`).

The NSE registers each alerted message. After successful receiving it returns
`notificationIdentifiersToRemove`: the delivered identifiers whose message, or a
later edit of it, is now read or deleted. Self-deleting messages are never reported
as deleted, even when their sender deletes them. The host removes those alerts.
Updating the content of delivered alerts is not supported.

When every event through the target was processed without deferral or app handoff,
the result also returns `alerts`: up to ten other unread messages stored without an
alert, oldest first, filtered by the same preview rules. A request with no alert of
its own shows the newest of them instead. Kalium marks them notified before
returning, so post them as local notifications after reporting calls and before
completing the request. While running, the app must mark conversations notified as
the Android app does, or the NSE alerts unread messages the app received.
[ADR 11](../docs/adr/0011-nse-account-runtime-ownership.md#app-handoffs-and-presentation)
explains their selection.

## Receiving and replay

The account lease covers all resource use, drain and cleanup. The NSE resumes the
same `Events` queue as the app, stores whole REST pages atomically and advances the
app's cursor only for non-transient events. It scans stored pending rows in order
to the end of the queue. A message-only notification that fails on network or
recoverable MLS state is deferred for 30 seconds, and later notifications that do
not depend on it can still be processed; see
[conversation isolation](../docs/NSE_IMPLEMENTATION_STATUS.md#conversation-isolation-and-deferral).
Required effects share one SQL transaction per notification inside the owned
Proteus/MLS transactions. The processed marker is written only after crypto commits.

SQL and native crypto are separate stores. Replay reapplies fields and preserves
canonical message identity; native duplicates do not produce a second alert.
Exact access-change, promotion and connection-warning history is best effort:
interruption may add an extra system message. An access event before a missing
conversation is fetched applies to that row on retry, so it can differ from the
first fetched snapshot. Destructive metadata/MLS changes still block when they
would lose the state needed to recover ciphertext.

Typing, remote mute and in-call reactions are ignored by the NSE because it has
no live app UI or active call. Unknown event types, property keys/values and
feature settings follow the app's no-op behavior. Known unsupported required work
fails the attempt and retains the notification and later queue.

Some work is handed to the app. The NSE returns `SUCCESS` with `appWorkPending`
and leaves the event and later queue unprocessed for incremental sync:

- `LEGAL_HOLD` when app legal-hold transitions are pending, or a membership change
  or fetched profile disagrees with the stored legal-hold status. Legal-hold events,
  configuration and embedded content are otherwise NSE no-ops.
- `ACCOUNT_INVALIDATION` when any stored pending row removes this client or deletes
  the account. The NSE processes no row, writes a per-client invalidation marker and
  revokes readiness, without deleting stores. Later NSE opens are `NOT_PREPARED`;
  the app logs out instead of processing the queue.

Typing START/STOP is skipped for every sender and preference value; typing
preference property updates are still persisted for the app. InCallEmoji returns
after decoding, before the message handler's conversation checks. REMOTEMUTE
still passes those checks and calling JSON decoding before being ignored, so
missing/incomplete conversation metadata or malformed calling JSON can fail the
attempt. Both encrypted signals retain the common crypto and destructive-replay
guards. No live consumer or deferred UI/call handoff is required for these no-ops;
incoming-call presentation uses the event-only AVS path described below.

Text preview metadata and quotes are stored normally. With previews enabled,
image download/hash/decryption runs within the owned attempt. Image failure leaves
text usable; budget expiry/cancellation still aborts the attempt. Successful cache
files survive replay and unreferenced partial files are cleaned. Required explicit
message/backend-conversation deletion still performs physical file cleanup before losing
SQL paths, preserving shared/offline/draft/avatar files.

Conversation-clear signals are applied in the notification's SQL transaction. The
cutoff is the earlier of the signal time and the envelope time. The NSE deletes
messages through the cutoff with their unshared files and pending delivery
confirmations, stores the exact deleted IDs as a clear snapshot and acknowledges the
`PendingActions` row. Replay, in the NSE or the Apple app's clear handler, deletes
only the snapshot IDs, so later messages with the same timestamp survive. Snapshots
are not pruned. Local removal deletes the conversation only when nothing changed
after the cutoff and no later application message in the notification targets it. For an MLS target it queues a
native wipe; the NSE and the Apple app's pre-sync cleanup retry it and acknowledge
it after native commit, never wiping a group that a current conversation uses.
Legacy pending clear/remove rows are retired without deleting data. Android keeps
its existing clear handler.

App message deletion, expiration and direct conversation clear keep their original
asset-only cleanup scope. A failed asset-file deletion is logged and keeps its
`Asset` cache record; it does not prevent the app's message/content change. This
does not add a cleanup retry worker. App delete-for-me and backend conversation
deletion do not require the NSE's broader file cleanup. Direct clear/remove still
propagates required content and conversation-deletion failures.

Conversation-timer updates also retain separate failure policies: the app requires
the timer write and replay check, but treats the resulting system-message write as
best effort. The NSE requires all of those effects before completing the notification.

Existing app consumers handle pending delivery confirmations, proposal commits,
expiration timers, asset/Cells downloads and configuration notifications. The NSE
adds no worker or replacement queue. Its own durable records are narrow: MLS
wrong-epoch recovery intents and payload receipts with completion in the native
checkpoint, a saved unexpected rejoin response, MLS deletion and local-removal wipe
intents, clear snapshots, deferral retry hints, the account invalidation marker,
and call signals with their missed-call claims.

The NSE persists a receive-time expiration deadline for self-authored ephemeral
messages, but does not delete them when that deadline passes. The app resumes
their persisted deadlines through its existing expiration executor. Messages
received directly by the app keep an unstarted deadline until that executor runs;
other users' messages remain unstarted in both receiving paths.

## Incoming calls

The receiver uses the existing AVS artifact and the event lifecycle from the iOS
`feat/avs_for_nse` branch (inspected at `79ad8ca31d`). It retains one native handle
and callback context per account/client for the process lifetime. Request wrappers
attach their callbacks during a serialized batch; closing a request releases its
callbacks, while `wcall_event_end` evaluates the batch without freeing the handle.
Only `create`, `start`, `process` and `end` are used; no live calling engine starts.

Decrypted Proteus and MLS calling signals are collected in backend order after
each notification commits. Once the requested target is processed, AVS evaluates
the collected prefix as one batch; it is skipped when the scan deferred anything.
`KaliumNSEResult.calls` contains its incoming, missed and closed callbacks
independently of the result's `shouldNotify`, which describes the ordinary
notification. Caller names retain upstream conversation verification policy.
Message-preview preferences never change incoming names or missed-call preview
fields. Only self-authored signals may redirect to a different calling
conversation, preserving the shared app's authorization rule.

A missed callback claims its journal signal once, before the policy check, so
replay cannot show a stale alert. Its own `shouldNotify` follows Android's
missed-call policy: not archived, not self, newer than last read and last
notified, and allowed by mute and busy/away status. Its conversation and sender
names retain the upstream missed-call preview policy.
A process exit between the claim and iOS scheduling loses that alert.

The wire-android host uses `IncomingCallReporter` and its versioned
`CallNotification` contract. Incoming results carry
`handoffId`, the UUID of a durable account-scoped signaling record. The host
reports this UUID to CallKit and resumes the existing account's full AVS engine.
CallKit termination comes from that engine's callbacks, preserving its close reason.
The NSE never starts media or the full engine.

Raw signals are persisted in encrypted account metadata inside the receiving SQL
transaction. A separate readiness update after successful native crypto commit
precedes the processed marker. Unready records cannot be submitted to AVS. Marker
replay reuses the same signaling identity. The host submits ready records in stored
order and acknowledges native enqueue for its handle generation. A new handle
replays retained signals; presentation tombstones prevent reopening an ended UUID.
This is retryable delivery, not exactly-once native processing: `wcall_recv_msg`
acknowledges enqueue and exposes no completion barrier. Recovery needs another
push or host launch if the process dies before the platform reporting effect.
The journal keeps at most 4096 signals and drops the oldest when full.

Both AVS adapters resolve incoming callback references through the Call signal journal.
The first ready start request matching the qualified conversation, caller, message second
and caller client when supplied provides the stable presentation UUID. Repeated callbacks
and new engine generations reuse that identity; an ended presentation stays ended.
The NSE incoming lookup is read-only. Besides storing committed signals, the NSE writes only
missed-call claims; only the full app engine changes ringing and termination state.
AVS callbacks expose neither message ID nor session ID, so identical callback references
cannot distinguish separate real calls; selection remains deterministic rather than advancing
through tied signals.

The host must also link and embed AVS. The NSE module now applies the existing
[Apple AVS runtime plugin](../plugins/apple-avs-runtime/README.md): generate its
configuration with `:nse:generateAvsXcodeConfig`, include
`nse/build/kalium-apple-avs-runtime/xcode/KaliumAvsRuntime.xcconfig` in the NSE
target's configuration, and run `:nse:embedAvsForXcode` from that target's Xcode
build phase to embed/sign the matching runtime. Copying only `KaliumNSE.framework`
does not include its dynamic AVS dependency.

Native AVS 10.5.10 tests demonstrate that valid SETUP/GROUPSTART/CONFSTART responses
and REJECT from another local device can succeed without a CLOSED callback, both
on fresh native handles and after an earlier incoming batch. CANCEL can also
succeed silently. CONFEND does produce CLOSED in a separate batch. Therefore,
forwarding CLOSED alone does not implement answered-elsewhere, rejection, or
cancellation. The full `wcall_create` / `wcall_recv_msg` engine is different: retained-handle tests
cover answer, reject and cancel for one-to-one, legacy group, conference and MLS
conference calls. One-to-one closes with reasons 5, 10 and 4 respectively; group
answer/reject closes local ringing with reason 7 (still ongoing).

The host retains ownership of its completion handler and extension-expiry policy;
the Kotlin budget bounds receiving, so leave time for CallKit reporting as well.

Call presentation follows sync and is best effort. Expired or invalid native call
payloads do not prevent later call signals from being evaluated. Invalid outer
calling JSON or required conversation/crypto failures still roll back the current
notification. An interruption after processed markers, or native duplicate replay,
can lose call presentation. There is no new durable call queue, call-history
persistence or exactly-once CallKit guarantee.
MLS subconversations remain outside this path. Simulator fixtures verify the
returned call results; real CallKit/APNs behavior still needs a signed-device host run.

See [current coverage and exact dependencies](../docs/NSE_IMPLEMENTATION_STATUS.md)
for event families, native constraints and remaining blockers. See
[ADR 11](../docs/adr/0011-nse-account-runtime-ownership.md) for ownership, readiness
and transaction decisions. Migration 151 drops the former receipt table; accounts
must be prepared using the compatible current app schema.

## Verification

From the repository root:

```sh
python3 nse/scripts/verify-swift-receiving.py
python3 nse/scripts/verify-swift-receiving.py --help
```

The runner links the simulator framework and test-only fixture executables,
creates an isolated iOS 26.3 simulator, prepares real accounts and serves
notifications over authenticated loopback HTTPS. Swift constructs the production
receiver. The native fixture checks stored effects, processed markers, delivery
work and subsequent ciphertext; the real app then reopens each account and reads
through its existing consumers. The runner removes its simulator and TLS server.
Reports are written under `nse/build/reports/swift-receiving/`.

Choose one scenario, such as `--ordinary-events`, `--typing-events`,
`--calling-events`,
`--access-changes-events`, `--member-role-events`, `--accepted-connection-events`,
`--preview-image-events` or `--creation-events`. `--help` lists every supported
scenario. `--skip-build` uses already linked artifacts and should only be used
when those artifacts match the source being checked.

[Native receiving tests](../domain/messaging/receiving/src/appleTest/kotlin/com/wire/kalium/logic/sync/receiving/NotificationProcessorNativeTest.kt)
cover SQL/native/marker gaps, ownership, cancellation, failed writes and ordered
replay. X.509 scenarios use the local fixture server. Separate native artifact
probes document limitations; a passing simulator or extension-mode link is not
proof of signed-device or AVS extension safety.

## Durable subgroup proposals

The app proposal scheduler now persists subgroup deadlines in account Metadata,
observes them when sync is LIVE, and uses its existing native transaction and MLS
transport. Successful outer native commit precedes conditional acknowledgement.
Recoverable failures retry while that owner is active; cancellation retains work
for the next owner. A newly scheduled record cannot be erased by an old ACK.
Active subconversation group mappings are persisted in the SQL `Subconversation`
table, so they survive restarts; mappings lost before that table existed and missing
native groups are not restored. The NSE reads neither the mappings nor the deadlines,
and subconversation payloads still fail NSE receiving; background subgroup support
remains separate.
