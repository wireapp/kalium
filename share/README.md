# Kalium share sender

`KaliumShareSender` is the Swift entry point for sending from the iOS share extension
([ADR 12](../docs/adr/0012-sending-from-extensions.md)). It sends text, including links as text, to one
conversation of a prepared Apple account. The app and the extension share the account as described in
the [NSE README](../nse/README.md): same App Group root, Keychain settings, account identity and store
versions. Construct `KaliumShareConfiguration` with the same values as `KaliumNSEConfiguration`.

`assembleKaliumShareDebugXCFramework` and `assembleKaliumShareReleaseXCFramework` build the framework,
with the same `-Pwire.ios.simulatorOnly=true` and `-Pwire.ios.deviceOnly=true` switches as KaliumNSE.

## Choosing a conversation

`conversations` lists unarchived conversations the extension may send to, most recently active first.
It mirrors the app's interaction rules and leaves out conversations that need the app: legal hold the
user has not consented to, verification that degraded since the user acknowledged it, and MLS groups
this client has not established. `failureReason` names a runtime failure such as `BUSY` when the
account is in use, or `APP_LOCK` when the account's team enforces App Lock: the extension cannot unlock
it, so sharing stays in the app. The host must also refuse to share while the app's own App Lock
passcode is set.

## Sending

`send` holds the account for at most `budgetMilliseconds`:

1. It opens the WebSocket and keeps it open while it works. As the app's sender waits for live, it sends
   only over a live socket: if the socket does not reach live, it returns `OFFLINE` without storing a
   message.
2. It processes stored events, so MLS commits and client changes are current, then stores the message
   as pending and sends it through the app's sender.
3. It closes and joins the socket, processes what arrived and returns the alerts owed.
   The backend sends no push for events delivered over the open socket, so the host posts each
   `KaliumShareAlert` before it finishes. `presentGenericAlert` asks for a generic alert when events
   may remain unpresented.

The sender never weakens a consent or verification rule. Work only the app can do, such as committing
pending MLS proposals, joining or re-syncing a group, a stale MLS epoch, newly enabled legal hold or
slow sync, returns `OPEN_APP` and leaves the message failed, so the user can retry it in Wire.
`OFFLINE` and `FAILED` also leave a stored message failed. The extension never schedules retries.
