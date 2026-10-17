# 12. Sending From Apple Extensions

Date: 2026-10-13

## Status

Proposed

## Context

The iOS share extension must send a message to a conversation the user picks; later, notification
actions may send replies, read receipts and delivery receipts. Sending requires a live account: Proteus
needs current recipient clients and sessions, and MLS needs the current epoch, otherwise the backend
rejects the message (Proteus 412, MLS 409 stale message).

ADR 11 gives extensions an exclusive per-account lease and a slim runtime without CoreLogic, and the
NSE can now catch up to live over the WebSocket (bounded incremental sync). Message sending, however,
lives in `:logic` (`MessageSenderImpl` and its envelope, session, MLS, failure and legal-hold
collaborators), coupled to `:logic` repositories and `SyncManager`.

An extension cannot launch its containing app, has no reliable background time after it is dismissed,
and runs under tight memory limits. The backend sends no push for events it delivers over an open
legacy WebSocket.

## Decision

Extensions send through a slim sender in `:domain:messaging:sending`, below `:logic`, composed from
the same implementation the app uses. `:logic` keeps its public use cases and adapts its repositories
to the sender's narrow ports; extensions adapt the slim runtime's DAOs and APIs. There is one sending
implementation, and no CoreLogic in extensions.

While an extension sends, it keeps the account live: it holds the lease, keeps the WebSocket open,
and ingests and processes incoming events, so MLS commits and client changes arrive during the send.
Because the backend sends no push for events delivered over that socket, the extension owns their
presentation: processed messages owe alerts (ADR 11), and the extension posts them before it releases
the account. Before the hold ends it closes and joins the socket.

As the app's sender waits for live, an extension sends only once the socket is live. Pages are fetched
only after the socket opens, so no event falls between a page and the socket. When the socket does not
reach live within its budget, the extension does not send and stores no message. Like the Android
app's one-shot sync, nothing is paged after the socket closes: a frame it delivered but had not stored
got no push and is not alerted until the next sync stores it (ADR 11).

The sender never weakens a consent or verification rule to finish in an extension. When sending would
need app-only work (a legal-hold change that requires consent, an unacknowledged verification change, an
MLS group the client must join or reset, pending proposals that need a commit, a stale epoch, a slow
sync), the extension keeps the message as failed and asks the user to open Wire.

The extension processes stored events before it sends. The sender recovers from a stale epoch inside its
crypto transaction, and processing events there would deadlock, so the extension declines that recovery
instead of waiting for live.

The first share extension sends text, including links as text, to one conversation chosen from the
app's conversation list components. Assets follow separately, because uploads can outlive the
extension.

## Plan

Each step keeps the app's behaviour unchanged. The app's tests keep covering the moved code through the
app's adapters:

1. Proteus envelope creation (`MessageEnvelopeCreator`, including external payloads), with a port for
   the conversation's legal-hold status.
2. Proteus session establishment (`SessionEstablisher`), with a port for pre-key bundles.
3. MLS message creation (`MLSMessageCreator`), with ports for group lookup, pending proposals and joins.
4. Send-failure classification and client-list repair for Proteus 412 (`MessageSendFailureHandler`),
   with ports for users and clients.
5. The sending orchestration (`MessageSenderImpl`): status updates, retries, legal-hold gate,
   stale-epoch handling and live waiting, with ports for messages, conversations and a live session.
6. Extension adapters over the slim runtime, and a bounded hold that keeps the socket open, processes
   incoming events and returns unnotified message alerts using ADR 11's existing notification rules.
7. The share extension: text and link content, the conversation picker, sending and presentation.

## Consequences

The extraction touches the app's sending path, so every step needs characterization tests before it
moves code. Notification actions and receipts can reuse the slim sender later. Until the hold is
validated on signed devices (memory, time and push suppression while the socket is open), the share
extension stays behind a build flag.
