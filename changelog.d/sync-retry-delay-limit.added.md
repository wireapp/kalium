Added `SyncRequest.limitRetryDelay(maxDelay)`. While the request is active, a failed sync waits at most `maxDelay` before retrying, instead of backing off for up to 10 minutes. Apps can use it while the user has the app open.

  - ABI: additive for callers. `SyncRequest` gains an abstract method.
  - Source: custom `SyncRequest` implementations, such as test fakes, must implement it.
  - Migration: none for callers.
