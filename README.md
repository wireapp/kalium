# Kalium

[![JVM & JS Tests](https://github.com/wireapp/kalium/actions/workflows/gradle-jvm-tests.yml/badge.svg)](https://github.com/wireapp/kalium/actions/workflows/gradle-jvm-tests.yml)
[![codecov](https://codecov.io/gh/wireapp/kalium/branch/develop/graph/badge.svg?token=UWQ1P7DY7I)](https://codecov.io/gh/wireapp/kalium)

Kalium is Wire's Kotlin Multiplatform SDK for messaging, end-to-end encryption, conversation state, backup, calling integration, and Wire backend access.

It is primarily built for Wire clients and currently targets Android, JVM, and Apple platforms. JavaScript support exists only where individual modules enable it.

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
| iOS/macOS Apple Silicon | Supported | Requires unified CoreCrypto; see [iOS build guide](docs/IOS_BUILD.md). |
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
./gradlew detekt
```

JVM tests and the CLI need native libraries. Build them with:

```bash
make
```

The libraries are written to `native/libs`.

## Configuration

Kalium uses a few Gradle properties that consumers and contributors may need to set explicitly:

```bash
./gradlew <task> -PUSE_UNIFIED_CORE_CRYPTO=true
./gradlew <task> -Pkalium.providerCacheScope=LOCAL
```

- `USE_UNIFIED_CORE_CRYPTO` selects the unified `core-crypto-kmp` dependency. Apple builds require it to be `true`.
- `kalium.providerCacheScope` controls provider-level caches. Consumer builds should set it explicitly to `LOCAL` or `GLOBAL`; this repository uses `LOCAL` for standalone builds.

See [contributor setup](docs/CONTRIBUTING.md#build-configuration) for the longer explanation.

## Project Map

Kalium is split by layer:

- `core:*` contains shared models, utilities, logging, and cryptography.
- `data:*` contains networking, persistence, protobuf, and data mappers.
- `domain:*` contains feature domains such as messaging, backup, calling, cells, work, and user storage.
- `logic` is the public SDK entry point used by clients.
- `sample:*`, `tools:*`, and `test:*` contain development support code.

For module boundaries and source-set details, see [architecture](docs/ARCHITECTURE.md).

## Documentation

- [Contributor setup](docs/CONTRIBUTING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [iOS builds](docs/IOS_BUILD.md)
- [CLI sample](sample/cli/README.md)
- [Architecture decision records](docs/adr/)
