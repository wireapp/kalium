Added shared Keychain configuration for Apple app extensions, including an explicit access group
and locked-device accessibility options.

  - Breaking: the exported Apple initializer now includes the access group and accessibility.
  - Behavior: Kotlin callers that omit the new arguments keep `WhenUnlocked` and the target's default access group.
