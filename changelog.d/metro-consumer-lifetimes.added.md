### Added

- Added `UserSessionScope.createConsumer()` for explicitly owned use cases. A consumer shares selected repository wrappers across its use cases while retaining the user's existing storage, network, crypto and mutable state.
- Added `KaliumConsumer.close()` and `closeAndJoin()` to stop and drain consumer-owned work, and `assemble()` to reject construction that overlaps account invalidation. Consumers must be closed by their creator.

### Migration

Existing SDK facade accessors remain available with their existing getter and sharing behavior. Consumers adopting the new API create one consumer per creator instance and close it when that instance ends. Logout rejects outgoing consumers; a later login requires a new consumer.

Kotlin builds now require Kotlin 2.4.20 with Metro 1.4.5, including its published Native runtime. Kalium also updates SKIE to 0.10.15 and Mokkery to 3.5.1 for compiler compatibility.

### Compatibility

ABI: additive SDK API; checked-in dumps record the exact surface.
Source: existing facade signatures are retained; Kotlin consumers must use a compatible compiler.
Behavior: consumer repository sharing is opt-in. Account invalidation stops admission and drains owned work before platform cleanup. Existing provider cache policies and Apple account ownership remain in effect.
