`KaliumNSERequest.syncMode` and `KaliumNSESyncMode` are removed. The NSE always catches up over the WebSocket, as the app does, then processes the stored queue to its end; there is no REST-only receiving mode.

  - ABI: breaking
  - Source: Swift callers stop passing `syncMode`.
  - Behavior: every request opens the WebSocket. Without a live socket nothing is fetched, and a push whose event was not processed sets `presentGenericAlert`.
  - Migration: remove the `syncMode` argument.
