Added `ApplePersistenceConfig.keychainAccessibility` to choose when Kalium's Keychain entries on Apple can be read.

  - ABI: breaking for `ApplePersistenceConfig`, whose constructor takes `keychainAccessibility`; added `KeychainAccessibility`.
  - Source: breaking; `keychainAccessibility` has no default, so every app chooses it explicitly.
  - Behavior: null keeps Apple's default, `WhenUnlocked`, so nothing changes. Under it, Kalium can't read its tokens and database keys while the device is locked, e.g. when a push launches the app in the background. `AfterFirstUnlockThisDeviceOnly` keeps them readable while locked, once the device was unlocked after a restart.
  - Migration: pass `keychainAccessibility = null` for the current behavior. The value is part of every Keychain lookup: entries stored with another value are neither found nor overwritten (storing one again fails), so changing it on an existing install loses access to them. Choose it before an app ships. It applies on iOS only; the file-based macOS keychain ignores it.

Super Kalium retains `accessGroup` for app/extension sharing and replaces its previous
`accessibility: AppleKeychainAccessibility` parameter with the required nullable
`keychainAccessibility: KeychainAccessibility?`. Map `WHEN_UNLOCKED`, `AFTER_FIRST_UNLOCK`,
and `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY` to `WhenUnlocked`, `AfterFirstUnlock`, and
`AfterFirstUnlockThisDeviceOnly`, respectively, to retain the same protection class.
The NSE facade keeps its existing choices, adds the two new protection classes,
and maps all five to the new enum so app and extension configurations can match.
Database passphrase insertion uses the same attributes as its settings reader.
