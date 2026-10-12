Added SQLCipher encryption for the global and user databases on Apple, as on Android.

  - ABI: changed for the Apple `databaseDriver`, which takes an optional `passphrase`, and for the Apple `PlatformUserStorageProperties` constructor, which takes the user database secret; Kalium constructs it itself.
  - Source: breaking for direct callers of the Apple `PlatformUserStorageProperties` constructor, which now requires `userDbSecretProvider`, and for callers passing the Apple `databaseDriver` `config` block positionally instead of as a trailing lambda.
  - Behavior: with `KaliumConfigs.shouldEncryptData()`, the databases are encrypted with raw keys kept in the Keychain. SQLCipher comes with CoreCrypto's library; Kalium refuses to open an encrypted database if the SQLite in the process isn't SQLCipher.
  - Migration: databases that earlier versions stored unencrypted on Apple are not migrated and fail to open with a key. No production install has one; development installs need their app data cleared.
