# Kalium NSE receiver

`KaliumNSEReceiver` is the Swift entry point for a prepared Apple account. The
library receives supported Proteus/MLS events, persists shared app state and
returns a notification decision. It is a development integration: real host/APNs,
entitlement and signed-device lifecycle validation remain separate work.

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
notification ID, the user's `showMessageContent` privacy preference and a budget
between 1 and 25,000 milliseconds. Call `process` from Swift async code. The
[Swift fixture](src/swiftTest/Receive.swift) shows the concrete constructor and call.
Swift cancellation reaches the owned attempt.

The host owns iOS's single completion callback and its generic failure fallback.
`SUCCESS` with `shouldNotify == false` is successful silent processing, not failure.
An eligible result can be a message, conversation deletion or connection request.
A null body can mean a generic alert or a privacy restriction. The facade filters
text and names by the host preference and conversation/expiration state. It does
not update alerts already delivered by iOS.

## Receiving and replay

The account lease covers all resource use, drain and cleanup. The NSE resumes the
same ordered `Events` queue as the app, stores whole REST pages atomically and
advances the app's cursor only for non-transient events. It processes the stored
prefix through the requested target. Required effects share one SQL transaction
per notification inside the owned Proteus/MLS transactions. The processed marker
is written only after crypto commits.

SQL and native crypto are separate stores. Replay reapplies fields and preserves
canonical message identity; native duplicates do not produce a second alert.
Exact access-change, promotion and connection-warning history is best effort:
interruption may add an extra system message. An access event before a missing
conversation is fetched applies to that row on retry, so it can differ from the
first fetched snapshot. Destructive metadata/MLS changes still block when they
would lose the state needed to recover ciphertext.

Typing, remote mute and in-call reactions are ignored by the NSE because it has
no live app UI or active call. Unknown property keys/values and feature settings
follow the app's no-op behavior. Known unsupported required events retain the
notification and later queue. Legal-hold effects remain intentional NSE no-ops;
the app keeps its existing handling.

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

Conversation-clear signals only upsert the target into the existing `PendingActions`
table, in the same transaction as the notification effects. `CLEAR_CONVERSATION_CONTENT`
and `DELETE_CONVERSATION_LOCALLY` preserve the clear-versus-remove intent. `created_at`
is the incoming event timestamp. The NSE leaves conversation content, files and MLS
state intact. Main-app handling of these action types is intentionally a no-op:
they stay pending and are not acknowledged or deleted by the existing recovery consumers.
Direct clear signals received by the app continue using its existing handler.

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
adds no worker, checkpoint, outcome journal or replacement queue. A processed
target succeeds silently; missed-alert recovery after native duplicate replay is
an optional enhancement.

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
the collected prefix as one batch. `KaliumNSEResult.calls` contains its incoming,
missed and closed callbacks independently of `shouldNotify`, which describes the
ordinary notification. Caller names obey the host privacy setting and conversation
verification state. Only self-authored signals may redirect to a different calling
conversation, preserving the shared app's authorization rule.

The wire-android host uses `IncomingCallReporter` and its versioned
`CallNotification` contract, not the sample adapter below. Incoming results carry
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

The sample [KaliumNSECallKitReporter.swift](host/KaliumNSECallKitReporter.swift)
remains available for legacy hosts. Its payload differs from wire-android's host
contract and must not be connected to the new parser.

The host must also link and embed AVS. The NSE module now applies the existing
[Apple AVS runtime plugin](../plugins/apple-avs-runtime/README.md): generate its
configuration with `:nse:generateAvsXcodeConfig`, include
`nse/build/kalium-apple-avs-runtime/xcode/KaliumAvsRuntime.xcconfig` in the NSE
target's configuration, and run `:nse:embedAvsForXcode` from that target's Xcode
build phase to embed/sign the matching runtime. Copying only `KaliumNSE.framework`
does not include its dynamic AVS dependency.

The helper retains the legacy required payload fields (`accountID`,
`conversationID`, `shouldRing`, `hasVideo`, `callerName`) on every event, including
termination. Payload version 1 adds `callPayloadVersion`, `accountDomain`,
`conversationDomain`, `eventType` (`incoming`, `missed`, `closed`),
`messageTimeSeconds`, `callerID`, `callerDomain`, and optional `callerClientID` and
`closeReason` (the AVS integer reason, including 5 for answered elsewhere and 10
for rejection). `process` takes the account domain from the request; direct
`report` callers must supply it. Unknown call types and invalid conversation UUIDs
fail reporting rather than silently acknowledging unsupported work.

The adapter awaits reports in order and propagates errors/cancellation. It forwards
terminations even without an incoming event in the current invocation, and forwards
duplicates. It does **not** own the host call register, deduplicate events, or infer
a call-instance UUID from a conversation. Hosts must correlate these decisions to
an existing call instance before applying them. This payload is evidence, not a
complete instance-correlation contract: AVS callbacks do not supply a signal ID or
call-instance ID. Do not wire this adapter directly to a conversation-only host
termination path when successive calls can overlap delayed events.

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
can lose call presentation, just as it can lose an ordinary alert. There is no new
durable call queue, call-history persistence or exactly-once CallKit guarantee.
MLS subconversations remain outside this path. Simulator fixtures verify reporting
payloads through an injected reporter; real CallKit/APNs behavior still needs a
signed-device host run.

See [current coverage and exact dependencies](../docs/NSE_IMPLEMENTATION_STATUS.md)
for event families, native constraints and remaining blockers. See
[ADR 11](../docs/adr/0011-nse-account-runtime-ownership.md) for ownership, readiness
and transaction decisions. Migration 146 drops the former receipt table; accounts
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
