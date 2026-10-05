API 16 and 17 are now supported without enabling development APIs, allowing production builds to negotiate the existing Meetings APIs on Android and iOS. API 18 remains limited to development API mode.

  - ABI: unchanged.
  - Source: unchanged.
  - Behavior: production API negotiation can now select API 16 or 17 instead of falling back to API 15.
  - Migration: no consumer changes required.
