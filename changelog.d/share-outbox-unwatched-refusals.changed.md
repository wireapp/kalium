`KaliumShareOutbox.stopWaiting` lets the share extension stop following a handoff before the app finished it. The app then reports a refusal itself, through the new `refused` parameter of `UserSessionScope.serveShareExtension`. The app also claims handed-off shares while an earlier import waits for the account to catch up.

  - ABI: `serveShareExtension` takes `refused`, which breaks its callers; `stopWaiting` is additive.
  - Behavior: a share handed off while an import waits is claimed at once and imported after it, one at a time. A result the extension no longer waits for is taken by the app instead of being swept an hour later.
  - Migration: apps pass `refused` and tell the user, for example with a local notification. Extensions call `stopWaiting` when they stop following a handoff, and show the result it returns.
