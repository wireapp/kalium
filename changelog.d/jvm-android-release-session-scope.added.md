Added `CoreLogic.releaseSessionScope(userId)` on JVM and Android, matching Apple. It stops the account's work and closes its Proteus and MLS clients, network and database, so another process can use the account; `getSessionScope` reopens it.

  - ABI: additive
  - Source: additive
  - Behavior: the internal `deleteSessionScope` now also closes these resources on JVM and Android instead of leaving them open.
  - Migration: no action required.
