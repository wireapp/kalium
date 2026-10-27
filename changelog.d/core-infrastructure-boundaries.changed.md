Breaking low-level module change: infrastructure failures and exception adapters, `CoreLogger`,
`ConversationBatch`, `ConversationProtocolUpdateStatus`, `UploadAssetData`, and
`CipherSuite.signatureAlgorithm()` now live in `:data:data-mappers`, removing network, persistence,
and crypto implementation dependencies from `:core:common` and `:core:data`. Kotlin packages and
behavior are retained. Direct low-level consumers must add the data-mappers dependency, upgrade
Kalium artifacts together, and rebuild dependent binaries/frameworks. See
[the migration guide](../docs/guides/core-infrastructure-migration.md).
