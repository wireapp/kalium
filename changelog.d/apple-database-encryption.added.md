Added SQLCipher encryption for the global and user databases on Apple, as on Android.

  - ABI: changed for the Apple `databaseDriver`, which takes an optional `passphrase`, and for the Apple `PlatformUserStorageProperties` constructor, which takes the user database secret; Kalium constructs it itself.
  - Source: additive
  - Behavior: with `KaliumConfigs.shouldEncryptData()`, the databases are encrypted with raw keys kept in the Keychain. Databases that earlier versions stored unencrypted are encrypted when they are first opened, by one process or SDK instance at a time. SQLCipher comes with CoreCrypto's library; Kalium refuses to open an encrypted database if the SQLite in the process isn't SQLCipher.
