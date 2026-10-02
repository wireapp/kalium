A failed CoreCrypto open no longer leaves Proteus and MLS unusable for the rest of the session; only an open whose own cleanup failed stays blocked. Logout on Android and JVM continues when account work does not stop within the drain timeout. The NSE no longer gets stuck on a full call signal journal, a protocol update for an inaccessible conversation, or member profiles that cannot be fetched.

  - ABI and source: adds `CoreCryptoOpenCleanupException` to `core:cryptography`.
  - Behavior: users whose profiles cannot be fetched are stored as incomplete, as the app does. At capacity, the call signal journal drops its oldest signals.
  - Migration: no consumer changes required.
