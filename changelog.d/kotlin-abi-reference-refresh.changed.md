Updated the Kotlin ABI reference declarations to include the public APIs already available for folder management, unread filtering, pending connection observation, notification preferences, and share-extension handling.

- ABI: reference snapshots now match the existing compiled API; this update adds no further public signature changes.
- Source: existing Either-returning contracts have explicit architecture-rule exceptions, and the rule works in any checkout directory.
- CI: boot and select an iOS simulator for Keychain-backed tests, use the current JSON benchmark reports, and publish JVM failures reliably.
- Behavior: none.
