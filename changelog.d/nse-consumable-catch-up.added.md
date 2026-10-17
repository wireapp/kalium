`NotificationApi.acknowledgeEventsOnOpenSession` acknowledges consumable events on the session that is already open and reports failure, without opening a session. The NSE's sync-to-live catch-up uses the consumable stream when the client registered that capability.

  - ABI: additive; the new interface member has a default implementation.
  - Source: additive
  - Behavior: no change while the client has no consumable capability.
  - Migration: no action required.
