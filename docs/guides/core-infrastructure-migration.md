# Core infrastructure API migration

Infrastructure APIs now live in `:data:data-mappers`. This is a breaking change for consumers that
depend directly on `:core:common` or `:core:data` for these symbols. Kotlin package names and behavior
are retained, but module ownership and dependency metadata change. Upgrade all Kalium artifacts
together and rebuild dependent Kotlin/JVM and Kotlin/Native binaries; do not mix old core artifacts
with the new data-mappers artifact.

| Moved API | Previous module | New module |
| --- | --- | --- |
| `com.wire.kalium.common.error.*`, including `CoreFailure`, `NetworkFailure`, `MLSFailure`, wrapping/mapping functions, and `MLSTransportFailureSerialization` | `:core:common` | `:data:data-mappers` |
| `com.wire.kalium.logic.failure.LegalHoldEnabledForConversationFailure` | `:core:common` | `:data:data-mappers` |
| `com.wire.kalium.common.logger.CoreLogger` | `:core:common` | `:data:data-mappers` |
| `com.wire.kalium.logic.data.conversation.ConversationBatch` and `ConversationProtocolUpdateStatus` | `:core:data` | `:data:data-mappers` |
| `com.wire.kalium.logic.data.asset.UploadAssetData` | `:core:data` | `:data:data-mappers` |
| `com.wire.kalium.logic.data.mls.signatureAlgorithm` | `:core:data` | `:data:data-mappers` |

## Kotlin and Gradle consumers

If you use one of the moved APIs directly, add the adapter dependency in the source set that uses it:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":data:data-mappers"))
    }
}
```

Use `api` instead of `implementation` if your own library exposes these types to callers. Maven Central
consumers should add `com.wire.kalium:data-data-mappers:<same-kalium-version>`.
Existing Kotlin imports remain valid. The `:logic`, `:domain:messaging:receiving`, and
`:domain:usernetwork` modules expose the adapter dependency for their public failure contracts.

`Either`, `kaliumLogger`, `callingLogger`, and `logStructuredJson` remain in `:core:common`.
`CipherSuite`, `RetentionType`, and other transport-independent domain models remain in `:core:data`.
Consumers using only these foundations need no adapter dependency. `CoreLogger.init` still configures
the shared loggers and network, persistence, and crypto logging; its implementation now belongs to
the data layer.

## Kotlin/Native and Swift consumers

Rebuild consuming KLIBs and regenerate frameworks against the complete updated dependency set.
Custom frameworks that explicitly export the moved low-level APIs must depend on and export
`:data:data-mappers` instead of expecting `:core:common` or `:core:data` to own those declarations.
Inspect the regenerated framework header and update any module-qualified Swift references as needed;
unchanged Kotlin package names do not guarantee unchanged generated Swift/Objective-C names or
binary compatibility.

In the regenerated `KaliumLogic` framework, the failure types are named `Data_mappersCoreFailure`
and `Data_mappersNetworkFailure`. Update explicit Swift type annotations, casts, extensions, and
failure-case references to the regenerated names. Public SDK result properties that expose these
failures also use the new names. The Kotlin ABI check does not detect this generated Apple API change.
SKIE's generated failure enums now live under `Skie.DataDataMappers`.

## Scope of the cleanup

Transport wrappers still contain their existing DTOs, and failures retain their concrete exception
causes and serialization format. Their new owner is the infrastructure adapter layer. This change
removes infrastructure from the core compilation graph; it does not eliminate infrastructure work
from a full SDK build or promise a measured end-to-end speedup.
