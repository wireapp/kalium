Fixed shared non-JavaScript cryptography metadata compilation failing to resolve CoreCrypto declarations.

The `nonJsMain` implementation imports CoreCrypto types, but dependencies declared in platform source sets are unavailable to its separate metadata compilation. Resolve and extract the matching CoreCrypto common KLIB and supply it to `compileNonJsMainKotlinMetadata` as a compiler-only input, with a check for the expected artifact layout.
