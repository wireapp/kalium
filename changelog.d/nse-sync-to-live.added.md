`KaliumNSERequest.syncMode` selects bounded incremental sync (`KaliumNSESyncMode.TO_LIVE`) or target-only receiving (`TARGET`, the default). `KaliumNSEResult.presentGenericAlert` asks the host for a generic alert when receiving ran out of time or failed to catch up before the push's event, and `stoppedReason` reports why a sync-to-live attempt stopped. As in the app, catching up needs the WebSocket; there is no REST-only success.

  - ABI: additive
  - Source: Swift callers must pass `syncMode`, because Kotlin default arguments are not exported to Swift.
  - Behavior: unchanged with `TARGET`. A `FAILED` result with reason `TIMEOUT` now sets `presentGenericAlert`.
  - Migration: pass `syncMode: .target` to keep the current behavior.
