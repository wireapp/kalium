# 13. Sharing While the App Owns the Account

Date: 2026-10-17

## Status

Accepted

## Context

[ADR 11](0011-nse-account-runtime-ownership.md) lets one process at a time own an account: the app while it is in the foreground or in a call,
otherwise the NSE or the share extension, briefly. [ADR 12](0012-sending-from-extensions.md) lets the share extension send by
owning the account and keeping it live.

When the app owns the account, the share extension cannot. This is a normal situation: the user shares
from inside Wire to Wire, shares from another app during a Wire call, or uses iPad split view with Wire
in the foreground. Today the sheet says "Wire is busy" before the user can pick a conversation, because
listing conversations also opens the account.

The extension must not act as a second owner. It may not write the account database, use CoreCrypto,
refresh tokens, upload, or open a WebSocket while the app owns the account. A pending message written
behind the app would not be sent either: on iOS, Kalium does not schedule pending messages, and the
app's queries see another process's writes only when it takes the account back.

iOS does not let an app hide its own share extension from its share sheet: `excludedActivityTypes`
applies only to system activities.

## Decision

When the app owns the account, the share extension hands the share to the app. The app sends it with
its own use cases, as if the user had sent it from the conversation.

**The outbox.** Each account has an outbox in the App Group, outside the account directory and named
with ADR 11's account hash. An entry is a directory with a request and the extension's own copies of
the files. The request names the conversation, the text, each file's name, type and media size, and one
message ID per item. The extension writes the entry under `staging/` and publishes it by renaming it into
`ready/`. A rename within one file system is atomic, so the app never sees a partial entry. The
extension then posts a Darwin notification. Entries are protected until first unlock, because the app
may import during a call while the device is locked, and are excluded from backups.

**Claiming.** The app claims an entry by renaming it from `ready/` to `claimed/`, which also tells the
extension that the app has it. The app claims only while it owns the account, whether or not it is live
yet: when notified, and when it takes an account. Within the app, one importer at a time serves an
account. It claims on every notification, even while an earlier import waits for the account to catch up, and
imports one share at a time.

**Importing.** The app imports files first, then the text, as ADR 12 sends them. Each item keeps its
message ID from the request, and the app skips an item whose message already exists. A crash or a
background handoff during an import therefore stores nothing twice: the next import finishes the
entry. Catching up can change the rules, so the app first waits until the account caught up, then
checks the conversation with the extension's own rules (ADR 12's share blocks and file rules). If a
rule now refuses it, the app stores nothing. The import stores each message itself; only sending goes
on without it, so an import cancelled before a message is stored stores and sends nothing. Stored
messages belong to the app: they upload, send, fail and retry in the conversation like messages from
the composer. The app then writes a result for the extension (stored, or the reason it refused) and
removes the entry.

**The picker.** The extension cannot list conversations while the app owns the account, and it must not
open the app's database. It asks the app with a Darwin notification. For each account it owns, the app
writes a snapshot: the account name, its file rules, and the conversations the extension may share to,
built by the same code as the extension's own list. The extension deletes any old snapshot before it
asks, and deletes each snapshot once read. A snapshot holds conversation names, so it is written only on
request, protected while the device is locked, and excluded from backups. The app answers these
requests on their own, so a share being imported never keeps a sheet waiting for its list.

**The sheet.** When the app does not answer, the account is busy for another reason, usually the NSE.
Then the extension retries opening the account itself until its budget ends. If the account becomes
busy between listing and sending, the extension retries sending for a moment, then hands the share off. It
also hands the share off when the app wants the account while the extension uploads, or when uploading takes
too long (ADR 12). The sheet waits a few seconds for the app to claim the share, and a few more for its result.
If the app writes a refusal in time, the sheet shows the reason. Otherwise the sheet says Wire will send the share
when Wire is opened, and marks the entry as unwatched: from then on, the app tells the user with a local notification
if it refuses the share. The app writes its result before it looks for the mark, and the sheet marks before it looks
one last time for the result, so at least one of them sees the result; only one takes it. The sheet offers no cancel
while it waits.

**Not a second lock.** A lock that only the app holds would tell the extension that the app owns the
account. It would not tell it that the app will take the share, and checking two locks one after the
other races with the app releasing them. The app's claim answers both.

## Implementation

Kalium's share module holds the outbox (entries, claims, results and snapshots), which the extension and
the app both use, and the app's importer and snapshot writer, which take the app's sending as ports.
The app runs them with `serveShareExtension`, which stores each item through the composer's text and
asset use cases under the item's message ID. The iOS app posts and observes the Darwin
notifications and runs the importer in each account's foreground and call work; the share extension
shows the snapshot picker, hands the share to the app, waits for its answer and falls back as described
above.

## Consequences

Only the app imports. A share left in the outbox waits for the app to own the account live: the share
extension and the NSE do not send other shares, which keeps their memory and time budgets unchanged.
Entries and snapshots add plaintext copies in the App Group, protected like the account's own files
and removed once imported or read. A share that a rule refuses at import was shown as allowed in the
picker; the sheet reports it if the app's answer arrives in time, and the app reports it otherwise.
