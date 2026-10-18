The share extension can share while the app owns the account (ADR 13). `KaliumShareOutbox.askAppForConversations` and `appConversations` list an account's conversations through the app, and `handOff` and `handOffStatus` give the app a share to store and send. On Apple, `UserSessionScope.serveShareExtension(doorbell)` answers the extension while the app owns an account.

  - ABI: additive. `KaliumShareOutbox`, `KaliumShareHandOff` and `KaliumShareHandOffStatus` are new; `appleAccountFileKey`, `deleteShareOutbox`, `ShareOutboxServer`, `ShareOutboxSending` and `ShareOutboxFile` are new `@InternalKaliumApi`.
  - Source: no changes for existing callers.
  - Behavior: `conversations` still reports `BUSY` while another process owns the account; the host then asks the app. The app stores handed-off shares with its composer's use cases and rules, files first, then the text, each under the message ID the extension gave it. Logging out drops the account's outbox.
  - Migration: hosts run `serveShareExtension` inside each account's work, live or not, ring it when the extension posts its Darwin notification, and cancel it before releasing the account.
