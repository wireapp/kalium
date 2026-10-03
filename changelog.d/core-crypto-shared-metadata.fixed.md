Fixed shared non-JavaScript CoreCrypto metadata compilation without changing platform binding dependencies.

  - Source: retains `nonJsMain` and its public implementation declarations in shared metadata.
  - Build: resolves and extracts the matching CoreCrypto common KLIB as an input only to the non-JavaScript metadata compiler. This compiler-only configuration is not inherited by platform source sets or published as a new dependency.
  - Behavior: JVM and Android keep their existing standalone bindings, Android exclusions remain unchanged, and Apple keeps its existing KMP binding. Native resources and JavaScript behavior are unchanged.
  - Compatibility: consumers directly referencing generated CoreCrypto types still need to declare their own binding dependency. The build validates the expected common metadata artifact layout.
