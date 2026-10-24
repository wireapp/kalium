`KaliumShareSender.send` bounds its uploads and gives the account up when another process needs it (ADR 12). Uploads may hold the account for 30 seconds plus the time to upload the files at 128 KiB/s, at most five minutes. `KaliumShareSender.yieldAccount` stops a send that is still uploading. Both end the send with `OPEN_APP` and `UPLOAD_TIMEOUT` or `ACCOUNT_WANTED` after releasing the account, for the host to hand the share to the app.

  - ABI: additive. `KaliumShareSender.yieldAccount` is new.
  - Behavior: uploads no longer run as long as the host lets them. A send that already holds the account live finishes within `budgetMilliseconds`, and later sends from a sender that yielded return `ACCOUNT_WANTED` without taking the account.
  - Migration: hosts call `yieldAccount` when the app wants the account or the host leaves the foreground, and hand the share to the app on `ACCOUNT_WANTED` and `UPLOAD_TIMEOUT`, as on `BUSY`.
