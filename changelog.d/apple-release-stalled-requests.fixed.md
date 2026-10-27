On Apple, a background release no longer waits for requests that stall on a bad network. A message send keeps its crypto transaction until its request ends, which can take longer than iOS keeps the app running. After 5 seconds, the release now fails the account's HTTP requests as offline until it starts closing, so the account is still released in time.

  - ABI: additive (`AuthenticatedNetworkContainer.failRequests`).
  - Source: additive for callers; implementers of `AuthenticatedNetworkContainer` must add `failRequests`.
  - Migration: no action required. Sends failed this way end as no network, like any other network failure.
