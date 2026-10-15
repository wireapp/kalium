On Apple, `CoreLogic.suspendSessionScope` now stops sync and closes its WebSocket client before releasing account ownership, so the notification extension receives pushes for new events.

  - ABI: no change in `logic`; `AuthenticatedNetworkContainer` gains `closeWebSockets()`.
  - Source: additive for callers; external implementations of `AuthenticatedNetworkContainer` must implement `closeWebSockets()`.
  - Behavior: sync requests on a suspended session wait until `openSessionScope` reacquires the account. Previously, sync could keep its WebSocket open after release.
  - Migration: no action required unless you implement `AuthenticatedNetworkContainer`.
