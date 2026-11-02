# Kalium NSE receiver

`KaliumNSEReceiver` is the Swift entry point for a prepared Apple account. The
library receives supported Proteus/MLS events, persists shared app state and
returns a notification decision. Ownership, readiness and replay decisions are
recorded in [ADR 11](../docs/adr/0011-nse-account-runtime-ownership.md).

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
message alerts in `deliveredNotifications`, and set `appPresentsAlerts` while the app is in
the foreground and presents alerts itself: a busy or unprepared account, or legal-hold work
left for the app, then asks for no generic alert.
Call `process` from Swift async code. Like
the app, the NSE first catches up over the WebSocket: it opens it, stores the REST pages
fetched once it is open and closes it at live, then processes the stored queue to its
end. Without a live socket nothing is fetched, and a push whose event was not processed
sets `presentGenericAlert`
([ADR 11](../docs/adr/0011-nse-account-runtime-ownership.md#bounded-incremental-sync)). The
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
do not suppress or alter call presentation. `presentationReason`, `failureReason`
and `stoppedReason` are content-free diagnostic codes. `appWorkPending` names work
left in Events for the app (`LEGAL_HOLD` or `ACCOUNT_INVALIDATION`).

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

## What the NSE receives

The NSE resumes the app's `Events` queue and applies each notification's required
effects in one SQL transaction inside the owned Proteus/MLS transactions. A
message-only notification that fails on network or recoverable MLS state can be
deferred while later independent notifications are processed
([conversation isolation](../docs/adr/0011-nse-account-runtime-ownership.md#conversation-isolation-and-deferral)).
Any other failure fails the attempt and keeps the notification and the later queue
pending. Existing app consumers handle pending delivery confirmations, proposal
commits, expiration timers, asset/Cells downloads and configuration notifications;
the NSE adds no worker or replacement queue.

Supported, with the app's authorization, mapping and replay rules:

- Messages: text, edits, explicit deletion and delete-for-me, reactions, ephemeral
  messages, knocks, locations, composite messages and button actions, assets,
  multipart content with Cells attachments, quotes, link-preview metadata and, with
  `linkPreviewEnabled`, preview images. Image failure leaves text usable.
- Receipts, cross-device last-read, and member mute/archive preferences.
- Conversations: Proteus and MLS creation, including channels, meetings, one-to-one
  conversations and prepared-client welcomes; rename, receipt mode, message timer,
  access, roles, members, adminless reminders, Proteus → MIXED → MLS migration,
  backend deletion, clear history and self-device local removal.
- Connections: pending, sent, accepted, cancelled, blocked, ignored and
  missing-consent states, with the pending-request alert.
- Users and clients: profile and availability updates, user properties, added
  clients, removal of other clients, user deletion, team leave and the self user's
  legal-hold request.
- Feature configuration, E2EI and MLS settings, MLS migration settings and
  noninteractive X.509 credential checks.
- MLS proposals, buffered future messages and wrong-epoch recovery by external join
  when the remote epoch is ahead.
- Federation domain deletion and connection removal, meeting create/update/delete,
  and calling signals (see below).

The NSE persists a receive-time expiration deadline for self-authored ephemeral
messages, but does not delete them when that deadline passes; the app's expiration
executor does.

Ignored, as in the app or because the NSE has no live UI or call: typing, remote
mute, in-call emoji and hand raises, legal-hold configuration, history-client signaling,
explicitly ignored content and unknown event types. Typing preference properties
are still stored.

Handed to the app with `appWorkPending`, leaving the event and the later queue for
the app's incremental sync:

- `LEGAL_HOLD` when a user's legal hold is enabled or disabled, when app legal-hold
  transitions are pending, or when a membership change or fetched profile disagrees
  with the stored legal-hold status. Every later attempt stops at the same event until
  the app runs, so a push whose event waits behind it sets `presentGenericAlert`
  unless `appPresentsAlerts`.
- `ACCOUNT_INVALIDATION` when any stored pending row removes this client or deletes
  the account. The NSE processes no row, writes a per-client invalidation marker and
  revokes readiness, without deleting stores. Later NSE opens are `NOT_PREPARED`;
  the app logs out instead of processing the queue.

Not supported; the attempt fails and the notification stays pending:

- MLS subconversation payloads.
- Sequences whose replay would lose its inputs: old-group ciphertext followed in the
  same notification by a reset, a wipe or metadata naming a replacement group; and
  a conversation deletion, connection cancellation or federation deletion after
  earlier effects that need its metadata, or before its recreation in the same
  notification.
- Work that needs a backend response the NSE cannot replay: establishment commits
  other than wrong-epoch recovery, one-to-one creation that needs MLS establishment,
  legacy teammate one-to-one creation, a changed self MLS advertisement that needs a
  missing target, and receive-triggered remote MLS reset.
- Native limits: invalid group IDs, unmerged external groups, and malformed MLS input
  that CoreCrypto reports as `Other`.
- Different E2EI required flags or discovery URLs in one notification, and a changed
  default credential without a matching installed credential.
- Undecodable payloads of known event types.


## Preparing alerts with an existing app account

`UserSessionScope.notificationAlerts` uses the session's existing database owner.
The host must retain that session throughout the request; this API does not acquire
another Apple account runtime or extend the application's background lifetime.

`prepare` captures message identities without marking them notified or exposing
preview content. The snapshot stays in the owning process and expires after a
bounded monotonic deadline (20 seconds by default, at most 30 seconds). Abandoning
it requires no cleanup. `commit` rechecks unread state, notification eligibility
and the saved content preference in a writer transaction before advancing the
conversation cursors. It returns `Committed`, `Expired`, or `Stale`; the last two
leave notification state unchanged. A stale snapshot needs fresh preparation, for
example when an older server event arrived after the snapshot. Overlapping claims
use the persisted cursors to avoid returning the same alert twice. Read failures
in the content preference hide text, names and typed preview labels.

Commitment also stores pending message identities in the same writer transaction.
`pending.read()` rebuilds current previews from canonical rows, rechecks eligibility
and removes obsolete entries. `pending.acknowledge()` is called after native enqueue
succeeds, or when iOS already contains that identity. Pending records contain no
message text or contact names. The app can observe changes through the existing
session without polling or opening another runtime. NSE also returns pending
messages in `alerts`, including its primary message when covered by the queue; the
native host must suppress a second primary presentation in that case.
`processAndDeliver` awaits the native enqueue callback and acknowledges acceptance
before releasing the account lease. Cancellation stops waiting for the native
callback so runtime cleanup can release the account. A late completion cannot
access the account database; an unacknowledged or failed enqueue stays pending
for identity-based reconciliation on the next attempt. Only an acknowledgement
already in progress is drained before release. Pending replay is oldest-first,
including records written by earlier versions. The legacy
`process` entry point only returns presentation data and does not acknowledge it.

A committed result is a claim to present, **not acknowledgement of OS delivery**.
The host must use a stable system request identity and reconcile delivered/pending
requests before retrying after a crash. iOS has no durable receipt for an alert the
user already dismissed, so exactly-once visible presentation cannot be guaranteed
across a crash after enqueue but before acknowledgement. Android presentation is
unchanged. This API does not extend background execution or implement uploads.

## Incoming calls

The receiver uses AVS's event-only API. It retains one native handle and callback
context per account/client for the process lifetime. Request wrappers attach their
callbacks during a serialized batch; closing a request releases its callbacks, while
`wcall_event_end` evaluates the batch without freeing the handle. Only `create`,
`start`, `process` and `end` are used; no live calling engine starts.

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

Incoming results carry `handoffId`, the UUID of a durable account-scoped signaling
record. The host reports this UUID to CallKit and resumes the existing account's
full AVS engine. CallKit termination comes from that engine's callbacks, preserving
its close reason. The NSE never starts media or the full engine.

Committed signals are kept in the account's call signal journal, which keeps at
most 4096 signals and drops the oldest when full. The full app engine replays them
and resolves AVS callbacks to the same `handoffId`, so repeated callbacks and new
engine generations reuse one identity and an ended presentation stays ended. This
is retryable delivery, not exactly-once native processing: recovery needs another
push or host launch if the process dies before the platform reporting effect.

The host must also link and embed AVS. The NSE module applies the
[Apple AVS runtime plugin](../plugins/apple-avs-runtime/README.md): generate its
configuration with `:nse:generateAvsXcodeConfig`, include
`nse/build/kalium-apple-avs-runtime/xcode/KaliumAvsRuntime.xcconfig` in the NSE
target's configuration, and run `:nse:embedAvsForXcode` from that target's Xcode
build phase to embed/sign the matching runtime. Copying only `KaliumNSE.framework`
does not include its dynamic AVS dependency.

The host retains ownership of its completion handler and extension-expiry policy;
the Kotlin budget bounds receiving, so leave time for CallKit reporting as well.

Call presentation follows sync and is best effort. Expired or invalid native call
payloads do not prevent later call signals from being evaluated. Invalid outer
calling JSON or required conversation/crypto failures still roll back the current
notification. An interruption after processed markers, or native duplicate replay,
can lose call presentation. There is no call-history persistence or exactly-once
CallKit guarantee. MLS subconversations remain outside this path.

## Verification

From the repository root:

```sh
python3 nse/scripts/verify-swift-receiving.py
python3 nse/scripts/verify-swift-receiving.py --help
```

The runner links the simulator framework and test-only fixture executables,
creates an isolated iOS simulator, prepares real accounts and serves
notifications over authenticated loopback HTTPS. Swift constructs the production
receiver. The native fixture checks stored effects, processed markers, delivery
work and subsequent ciphertext; the real app then reopens each account and reads
through its existing consumers. The runner removes its simulator and TLS server.
Reports are written under `nse/build/reports/swift-receiving/`.

Choose one scenario, such as `--ordinary-events`, `--typing-events`,
`--calling-events`, `--access-changes-events`, `--member-role-events`,
`--accepted-connection-events`, `--preview-image-events` or `--creation-events`.
`--help` lists every supported scenario. `--skip-build` uses already linked
artifacts and should only be used when those artifacts match the source being
checked.

[Native receiving tests](../domain/messaging/receiving/src/appleTest/kotlin/com/wire/kalium/logic/sync/receiving/NotificationProcessorNativeTest.kt)
cover SQL/native/marker gaps, ownership, cancellation, failed writes and ordered
replay. X.509 scenarios use the local fixture server. The
[MLS stress test](scripts/MLS-STRESS.md) checks ownership and replay across app and
NSE processes.

Simulator runs do not cover APNs delivery, entitlements, the signed-device
extension lifecycle or AVS extension safety; those need a signed-device host run.
