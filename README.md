# Kalium

[![CI](https://github.com/wireapp/super-kalium/actions/workflows/ci.yml/badge.svg)](https://github.com/wireapp/super-kalium/actions/workflows/ci.yml)

Kalium is Wire's Kotlin Multiplatform SDK for messaging, end-to-end encryption, conversation state, backup, calling integration, and Wire backend access.

It is primarily built for Wire clients and currently targets Android, JVM, and Apple platforms. JavaScript support exists where individual modules enable it.

## Use Kalium

Release builds are published under the Maven group `com.wire.kalium`. The main SDK entry point is the `:logic` module:

```kotlin
dependencies {
    implementation("com.wire.kalium:logic:<version>")
}
```

Use the version from GitHub releases or Maven Central metadata.

## Supported Platforms

| Platform | Status | Notes |
| --- | --- | --- |
| Android | Supported | Main production target. |
| JVM | Supported | Used by tests, tools, and the CLI sample. |
| iOS/macOS Apple Silicon | Supported | Frameworks for the app, the Notification Service Extension and the share extension; see the [Apple build guide](docs/IOS_BUILD.md). |
| JavaScript | Limited | Experimental and module-dependent. |

## Build From Source

Requirements:

- JDK 21
- Git
- macOS on Apple Silicon for Apple target builds

Common commands:

```bash
./gradlew build
./gradlew jvmTest -Djava.library.path=./native/libs
./gradlew runMutationTests
./gradlew detekt
```

`runMutationTests` runs PIT against shared KMP code compiled for the JVM, so it does not need an emulator or device.
Reports are written per module under `build/reports/pitest`. Resource use and the required score can be configured
with `-Pmutation.maxParallelMutants=2`, `-Pmutation.maxHeap=2g`, `-Pmutation.timeoutConstantMs=1000`, and
`-Pmutation.threshold=60`. The setup does not mutate Kotlin/Native, Kotlin/JS, or Android-only binaries.

JVM tests and the CLI need native libraries. Build them with:

```bash
make
```

The libraries are written to `native/libs`.

## Configuration

Kalium uses a few Gradle properties that consumers and contributors may need to set explicitly:

```bash
./gradlew <task> -Pkalium.providerCacheScope=LOCAL
```

- `kalium.providerCacheScope` controls provider-level caches. Consumer builds should set it explicitly to `LOCAL` or `GLOBAL`; this repository uses `LOCAL` for standalone builds.

See [contributor setup](docs/CONTRIBUTING.md#build-configuration) for the longer explanation.

## Project Map

Kalium is split by layer:

- `core:*` contains shared models, utilities, logging, and cryptography.
- `data:*` contains networking, persistence, protobuf, and data mappers.
- `domain:*` contains feature domains such as messaging, backup, calling, cells, work, and user storage.
- `logic` is the public SDK entry point used by clients.
- `nse` and `share` build the frameworks for the iOS Notification Service Extension and share extension.
- `sample:*`, `tools:*`, and `test:*` contain development support code.

For module boundaries and source-set details, see [architecture](docs/ARCHITECTURE.md).

## Documentation

- [Contributor setup](docs/CONTRIBUTING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Apple builds](docs/IOS_BUILD.md)
- [Apple AVS runtime plugin](plugins/apple-avs-runtime/README.md)
- [Notification Service Extension](nse/README.md)
- [Share extension](share/README.md)
- [Message timeline](docs/guides/message-timeline.md)
- [Conversation list](docs/guides/conversation-list.md)
- [Apple call tracing](docs/guides/apple-call-tracing.md)
- [CLI sample](sample/cli/README.md)
- [Architecture decision records](docs/adr/)
