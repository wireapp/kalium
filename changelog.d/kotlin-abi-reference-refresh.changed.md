Updated the Kotlin ABI reference declarations to include the public APIs already available for folder management, unread filtering, pending connection observation, notification preferences, and share-extension handling.

- ABI: reference snapshots match the existing compiled API, with explicit architecture-rule exceptions for existing Either-returning contracts. No new public signatures are introduced.
- Compatibility: shared messaging code now compiles for JS and uses supported dispatchers and optional host filesystems.
- Database: multipart reads await asynchronous SQL results, and receiving effect transactions remain serialized while queries suspend.
- CI: prepare an iOS simulator for Keychain-backed tests, consume the current benchmark reports, run shared JS database tests against isolated worker-backed storage, and upload test reports to Datadog in batches.
