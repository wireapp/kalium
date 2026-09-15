JVM on Linux: the master key of the encrypted settings now lives in the Secret Service (GNOME Keyring, KDE Wallet 5.97+, KeePassXC), reached through libsecret.

  - ABI: no change
  - Source: no change
  - Behavior: without libsecret, or without a running and unlockable Secret Service, Kalium throws `SettingsEncryptionException` while `KaliumConfigs.shouldEncryptData` is on. There is no plaintext fallback.
  - Migration: none needed.
