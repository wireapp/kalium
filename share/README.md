# Kalium share sender

`KaliumShareSender` is the Swift entry point for sending from the iOS share extension
([ADR 12](../docs/adr/0012-sending-from-extensions.md)). It sends text, including links as text, and up to
20 files to one conversation of a prepared Apple account. The app and the extension share the account as described in
the [NSE README](../nse/README.md): same App Group root, Keychain settings, account identity and store
versions. Construct `KaliumShareConfiguration` with the same values as `KaliumNSEConfiguration`.

`assembleKaliumShareDebugXCFramework` and `assembleKaliumShareReleaseXCFramework` build the framework,
with the same `-Pwire.ios.simulatorOnly=true` and `-Pwire.ios.deviceOnly=true` switches as KaliumNSE.

## Choosing a conversation

`conversations` lists unarchived conversations the extension may send to, most recently active first.
It mirrors the app's interaction rules and leaves out conversations that need the app: legal hold the
user has not consented to, verification that degraded since the user acknowledged it, and MLS groups
this client has not established. `acceptsFiles` is false for Cells conversations, which take files through
Cells; only the app uploads there. `failureReason` names a runtime failure such as `BUSY` when the
account is in use, or `APP_LOCK` when the account's team enforces App Lock: the extension cannot unlock
it, so sharing stays in the app. The host must also refuse to share while the app's own App Lock
passcode is set.

`files` holds the account's file rules, as the app applies them: the file types its build allows, and the
size limits (15 MB for images Wire displays, 100 MB for other files in a team, 25 MB otherwise). It is null
when the team disabled file sharing, or the setting cannot be read. `KaliumShareFiles.refusal` lets the host
explain a refused file before sending; `send` checks again.

## Uploading files

The host prepares each `KaliumShareAttachment`: a file it keeps until `send` returns, its name, mime type
and, for images, videos and audio, dimensions and duration. Before it holds the account live, `send`
checks the conversation and the file rules, then encrypts and uploads each file as the app does: private,
persistent, AES-256-CBC with the IV prefixed. It streams each file and hashes the ciphertext as it writes
it, so no file is held in memory, and reports progress over the plain size through `KaliumShareProgress`.

Uploads run outside `budgetMilliseconds`, and the account stays held meanwhile. They end when they finish,
fail or the host cancels, and with `OPEN_APP` when they run past their own budget (`UPLOAD_TIMEOUT`: 30
seconds plus the files' size at 128 KiB/s, at most five minutes) or when the host calls `yieldAccount`
(`ACCOUNT_WANTED`), because the app needs the account. The host then hands the share to the app, after
posting the result's alerts. `yieldAccount` also stops the live hold while it catches up; a send that stored
a message finishes within its budget. Nothing is stored until every upload
succeeded, so a failed or stopped upload leaves nothing behind in Wire but the uploaded files on the backend.

## Sending

`send` holds the account for at most `budgetMilliseconds`:

1. It opens the WebSocket and keeps it open while it works. As the app's sender waits for live, it sends
   only over a live socket: if the socket does not reach live, it returns `OFFLINE` without storing a
   message.
2. It processes stored events, so MLS commits and client changes are current, then stores each message
   as pending and sends it through the app's sender: each file as an asset message, then the text, as a
   caption follows what it describes.
   A stored file message is uploaded, and the app shows it from a local copy in the account's asset
   directory. Once a message is not sent, or sending runs past its soft deadline or its budget, the
   remaining messages are stored as failed. The file rules are checked again after catching up, which can
   apply a change to them.
3. It closes and joins the socket, processes what arrived and returns the alerts owed.
   The backend sends no push for events delivered over the open socket, so the host posts each
   `KaliumShareAlert` before it finishes. `presentGenericAlert` asks for a generic alert when events
   may remain unpresented.

The sender never weakens a consent or verification rule. Work only the app can do, such as committing
pending MLS proposals, joining or re-syncing a group, a stale MLS epoch, newly enabled legal hold or
slow sync, returns `OPEN_APP` and leaves the message failed, so the user can retry it in Wire.
`OFFLINE` and `FAILED` also leave a stored message failed. The extension never schedules retries; the app
resends a failed file message without uploading it again.

## While the app owns the account

`conversations` and `send` report `BUSY` while another process owns the account. The NSE owns it briefly, but
the app owns it as long as it is in the foreground or in a call. Then the extension hands the share to the
app ([ADR 13](../docs/adr/0013-sharing-while-the-app-owns-the-account.md)) through a per-account outbox in the
App Group:

1. `KaliumShareOutbox.askAppForConversations` asks the app to list the account's conversations, and the host rings the app
   with a Darwin notification. `appConversations` returns the app's answer once, built by the same code as
   `conversations`. When the app does not answer, something else owns the account for a moment; retry
   `conversations`.
2. `handOff` copies the files (as clones, where the file system allows) next to the request and publishes
   it, and the host rings the app again. `handOffStatus` follows it: `WAITING` until the app claims it,
   `CLAIMED` while the app stores it, then `STORED` or `NOT_STORED` with a reason, reported once.

The app runs `serveShareExtension` while it owns an account, live or not. It answers requests for conversations,
claims handed-off shares and stores them with its composer's use cases, files first, then the text. Each
item keeps the message ID the extension gave it, so an import interrupted by a background handoff never
stores a message twice. Once the account caught up, the app checks the conversation and file rules again before it
stores anything. Requests for conversations are answered meanwhile, on their own.
