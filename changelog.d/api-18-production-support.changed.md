API 18 is now supported without enabling development APIs on Android and iOS.

  - ABI: unchanged.
  - Source: unchanged.
  - Behavior: production API negotiation can now select API 18 instead of falling back to API 17. Versions advertised only as backend development APIs still require development API mode.
  - Migration: no consumer changes required.
