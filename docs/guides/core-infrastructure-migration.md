# Core infrastructure API migration

Public failures now live in `:core:failures`; infrastructure adapters live in `:data:data-mappers`.
This is a breaking low-level API change. Upgrade all Kalium artifacts together and rebuild dependent
Kotlin/JVM and Kotlin/Native binaries. Existing failure class package names are retained, but their
module ownership and transport-specific members change.

| API | New owner |
| --- | --- |
| `CoreFailure`, `NetworkFailure`, `MLSFailure`, `ProteusFailure`, `StorageFailure`, `E2EIFailure`, `EncryptionFailure`, and pure failure helpers | `:core:failures` |
| `LegalHoldEnabledForConversationFailure` | `:core:failures` |
| `ServerError`, `MlsErrorDetails`, failure predicates, and `AuthenticationCodeFailure` | `:core:failures` |
| `wrapApiRequest`, crypto/storage wrapping functions, platform exception conversion, `CommonizedMLSException`, and `MLSTransportFailureSerialization` | `:data:data-mappers` |
| `CoreLogger` initialization | `:data:data-mappers` |
| `ConversationBatch`, `ConversationProtocolUpdateStatus`, `UploadAssetData`, and `CipherSuite.signatureAlgorithm()` | `:data:data-mappers` |

## Dependencies

Libraries exposing failures should depend on the small contract module:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:failures"))
        // Only if this module calls infrastructure adapters:
        implementation(project(":data:data-mappers"))
    }
}
```

Maven Central coordinates are `com.wire.kalium:core-failures:<version>` and
`com.wire.kalium:data-data-mappers:<version>`. `:logic` exposes the failure contract transitively;
SDK consumers using only public results need no direct adapter dependency. The receiving,
user-network, sending, cells, and conversation-history modules also expose their failure contracts.

`Either`, `kaliumLogger`, `callingLogger`, and `logStructuredJson` remain in `:core:common`.
Transport-independent models such as `CipherSuite` and `RetentionType` remain in `:core:data`.
`CoreLogger.init` still initializes logging across the infrastructure modules from the data layer.

## Server errors

Replace `ServerMiscommunication.kaliumException` and casts to `KaliumException` with `reason` and
`ServerError` from `com.wire.kalium.common.error`:

```kotlin
val reason = (failure as? NetworkFailure.ServerMiscommunication)?.reason
if (reason is ServerError.InvalidRequest && reason.isNotFound()) {
    // Handle a 404 without importing the network module.
}
```

`ServerError.InvalidRequest.errorResponse` has `code`, `label`, and `message` values, and
`retryAfterSeconds` retains the parsed server delay. `Server`, `Redirect`, and `Unauthorized` retain
the corresponding response details. `AdminlessConversation` retains the response details and its
`eligibleMembers` contains domain user IDs.
The `isNotFound`, `isTooManyRequests`, authentication-code and other predicates now have overloads in
`com.wire.kalium.common.error` for these public values. Use that package's `AuthenticationCodeFailure`
when inspecting a normalized failure; the network-layer enum still applies to raw network exceptions.

To construct a failure without infrastructure, use
`NetworkFailure.ServerMiscommunication(ServerError.InvalidRequest(ServerError.Response(code, message, label)))`.
Infrastructure code converts a raw exception with `serverMiscommunication(exception)` from
`:data:data-mappers`. Use `rootCause` only for diagnostics; it is nullable and typed as `Throwable`,
and business decisions must not require a concrete exception to be present.

## Crypto and MLS errors

Replace `ProteusFailure.proteusException.code` with `ProteusFailure.code` and compare against
`ProteusFailure.Code`. `intCode` retains the original numeric code, while `rootCause` retains the
original exception for diagnostics. Infrastructure code constructs it with `proteusFailure(exception)`;
contract-only code can construct `ProteusFailure(ProteusFailure.Code.SESSION_NOT_FOUND, intCode = 2)`.

`MlsMessageRejectedFailure.Other.remoteCause` now holds `MlsErrorDetails(label, message)` rather than a
network DTO. The serialized MLS transport payload retains its existing `remoteCause.label` and
`remoteCause.message` shape; existing payloads remain readable. Federation domains and missing-user IDs
are preserved by the adapters.

## Kotlin/Native and Swift

Rebuild consuming KLIBs and regenerate frameworks against the complete updated dependency set.
Custom frameworks that explicitly export public failures should depend on and export `:core:failures`.
Only frameworks intentionally exposing adapter APIs should export `:data:data-mappers`.

Module ownership changes generated Swift/Objective-C names even where Kotlin package names remain
unchanged. Inspect the regenerated header and update explicit annotations, casts, extensions and
failure-case references, including those used in public SDK results. The Kotlin ABI check does not
detect these generated Apple API changes. The linked iOS simulator framework now exports
`FailuresCoreFailure`, `FailuresNetworkFailure`, and `FailuresProteusFailure`; migrate explicit
references from the previous `Common…` names. SKIE failure-case namespaces move from
`Skie.CoreCommon` to `Skie.CoreFailures`.

## Build impact

The contract and foundational core modules compile without network, persistence, protobuf, or crypto
implementation projects. The SDK's public dependency graph excludes `:data:data-mappers`, so unrelated
adapter API changes do not enter consumers' compile classpaths through this dependency. Full SDK
builds still compile the adapters and infrastructure. Build measurements must distinguish consumer
compile avoidance, core-only compilation, and full SDK compilation.

See the [local build validation](core-boundary-build-validation.md) for the measured consumer task
outcomes, timings, limitations, and reproduction steps.
