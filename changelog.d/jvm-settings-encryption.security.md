JVM: with `KaliumConfigs.shouldEncryptData` on, which is the default, the settings files are now encrypted with AES-256-GCM. They hold the auth tokens and the keys of the CoreCrypto keystores. Their master key lives in the system key store; on macOS that is the Keychain.

  - ABI: no change
  - Source: no change
  - Behavior: a `settings-master-key` file next to the settings refers to the key. Kalium throws `SettingsEncryptionException` instead of starting with empty settings when that key is missing or can't be read, or when a settings file is plaintext or was changed. Systems without a supported key store fail the same way; Windows and Linux support follows.
  - Migration: existing plaintext settings files are not migrated.
