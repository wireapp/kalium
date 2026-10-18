`KaliumShareSender` sends files. `KaliumShareRequest.attachments` takes up to 20 prepared files, which `send` checks against the account's file rules and uploads before it holds the account live, reporting progress through `KaliumShareProgress`. Each file becomes an uploaded asset message, followed by the text. `KaliumShareConversations.files` and `KaliumShareConversation.acceptsFiles` let the host explain refused files and Cells conversations before sending.

  - ABI: breaking
  - Source: Swift callers pass `attachments` to `KaliumShareRequest` and a `progress` (or nil) to `send`.
  - Behavior: `budgetMilliseconds` now bounds the live hold, not opening and closing the account; uploads run before it, until they finish, fail or the host cancels. Once a message is not sent, or sending passes its soft deadline, the remaining messages are stored as failed.
  - Migration: pass an empty `attachments` list and a nil `progress` to keep sending text only.
