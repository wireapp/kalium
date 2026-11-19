Added debug-only manual recovery for MLS key packages left on the server after a CoreCrypto transaction rolls back. Recovery commits fresh local private keys before replacing the current client's server pool for its active cipher suite, and can attempt to repair pending MLS conversations.

- ABI: additive JVM and Android recovery result variants; the reference snapshots now include `ResetMLSKeyPackagesResult.Success` and `ResetMLSKeyPackagesResult.Failure`.
- Source: existing callers are unchanged.
- Behavior: recovery runs only when explicitly requested through the debug APIs.
- Migration: no action is required for existing consumers.
