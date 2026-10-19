Added `ApplePersistenceConfig.accessGroup` so an app and its extensions can share Kalium's Keychain
entries through an explicit access group.

  - ABI: changes the `ApplePersistenceConfig` constructor and `copy`, which `keychainAccessibility` also changes.
  - Source: additive; `accessGroup` defaults to null.
  - Behavior: null keeps the target's default access group.
