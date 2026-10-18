A failed CoreCrypto open no longer leaves Proteus and MLS unusable for the rest of the session; only an open whose own cleanup failed stays blocked. Logout on Android and JVM continues when account work does not stop within the drain timeout.

  - ABI and source: adds `CoreCryptoOpenCleanupException` to `core:cryptography`.
  - Migration: no consumer changes required.
