# 12. Sending From Apple Extensions

Date: 2026-10-13

## Status

Accepted. Amended 2026-10-25: uploads have a budget and give the account up when the app needs it.

## Context

The iOS share extension must send a message to a conversation the user picks; later, notification
actions may send replies, read receipts and delivery receipts. Sending requires a live account: Proteus
needs current recipient clients and sessions, and MLS needs the current epoch, otherwise the backend
rejects the message (Proteus 412, MLS 409 stale message).

[ADR 11](0011-nse-account-runtime-ownership.md) gives extensions an exclusive per-account lease and a
slim runtime without CoreLogic, and the NSE can catch up to live over the WebSocket (bounded
incremental sync). Message sending, however,
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
app's conversation list components.

Files follow in the same extension. Uploads can outlive a dismissed extension, so the share sheet
stays open while they run: the extension encrypts and uploads every file before it holds the account
live, reports progress, and stops when the user cancels. Uploading needs the account's tokens, so the
extension holds the account meanwhile, but not its socket, and the live hold's budget starts after the
uploads. Holding the account keeps it from the app and the NSE, so uploads have a budget of their own,
enough at about 1 Mbit/s and at most five minutes, and they stop when the app needs the account: the app
posts a Darwin notification when it enters the foreground or a call arrives, and the extension stops when
its host leaves the foreground, because iOS then suspends it with the account held. The extension then
releases the account and hands the share to the app (ADR 13), which uploads the files again. The same
applies once the hold's socket is live, while it catches up; the connection itself is not interrupted.
Once the extension stored a message, the send finishes within its budget: stopping it then could send the
message twice.
Nothing is stored until every upload succeeded; each file then becomes an uploaded asset
message, so a message that fails to send is retried in the app without uploading again. The extension
follows the app's file rules: file sharing, allowed types and size limits. Cells files use the app's
Cells transport and are published together before one multipart message is stored with its caption.
This path takes explicit files and never reads or consumes the conversation's composer draft.

## Implementation

The app's sending was extracted step by step, keeping its behaviour unchanged and its tests covering
the moved code through the app's adapters: Proteus envelope creation (`MessageEnvelopeCreator`),
session establishment (`SessionEstablisher`), MLS message creation (`MLSMessageCreator`), send-failure
handling (`MessageSendFailureHandler`) and the sending orchestration (`MessageSenderImpl`), each with
narrow ports. Extension adapters over the slim runtime provide a bounded hold that keeps the socket
open, processes incoming events and returns unnotified message alerts. The share extension sends text
and files.

## Consequences

The app and extensions share one sending path, so changes to it affect both. Notification actions and
receipts can reuse the slim sender later. The hold still needs validation on signed devices (memory,
time and push suppression while the socket is open).
