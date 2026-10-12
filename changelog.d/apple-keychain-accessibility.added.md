Added `ApplePersistenceConfig.keychainAccessibility` to choose when Kalium's Keychain entries on Apple can be read.

  - ABI: breaking for `ApplePersistenceConfig`, whose constructor takes `keychainAccessibility`; added `KeychainAccessibility`.
  - Source: breaking; `keychainAccessibility` has no default, so every app chooses it explicitly.
  - Behavior: null keeps Apple's default, `WhenUnlocked`, so nothing changes. Under it, Kalium can't read its tokens and database keys while the device is locked, e.g. when a push launches the app in the background. `AfterFirstUnlockThisDeviceOnly` keeps them readable while locked, once the device was unlocked after a restart.
  - Migration: pass `keychainAccessibility = null` for the current behavior. The value is part of every Keychain lookup: entries stored with another value are neither found nor overwritten (storing one again fails), so changing it on an existing install loses access to them. Choose it before an app ships. It applies on iOS only; the file-based macOS keychain ignores it.
