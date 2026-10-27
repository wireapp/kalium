Breaking low-level API change: public failures now live in `:core:failures`, with normalized
`ServerError`, `MlsErrorDetails`, and `ProteusFailure.Code` values replacing concrete network/crypto
exception types and DTOs. Adapters, `CoreLogger`, transport wrappers, and cipher-suite wire mapping
live in `:data:data-mappers`, which stays behind SDK `implementation` dependencies. Upgrade Kalium
artifacts together, update error-detail access and dependencies, and rebuild consuming binaries and
frameworks. See [the migration guide](../docs/guides/core-infrastructure-migration.md).
