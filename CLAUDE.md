# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kalium is a Kotlin Multiplatform (KMP) messaging SDK for the Wire messaging platform. It handles end-to-end encryption, messaging protocols, voice/video calling, and backup functionality across JVM, Android, iOS, and JavaScript platforms.

**Requirements:** JDK 21, macOS Apple Silicon for iOS builds

**Key Technologies:**
- Kotlin 2.2.21 with Kotlin Multiplatform
- Gradle with Kotlin DSL
- SQLDelight 2.2.1 for database layer (SQLCipher encrypted)
- Ktor 3.3.2 for HTTP networking
- CoreCrypto 9.1.3 + libsodium for cryptography (MLS protocol)
- AVS 10.1.33 for audio/video calling
- Protocol Buffers (pbandk) for serialization

## Build Commands

```bash
# Clean build
./gradlew clean build

# JVM tests (requires native libraries)
./gradlew jvmTest -Djava.library.path=./native/libs

# Android unit tests
./gradlew testDebugUnitTest                    # All modules
./gradlew androidUnitOnlyAffectedTest          # Only affected modules
./gradlew :logic:testDebugUnitTest             # Single module

# Run single test class
./gradlew :logic:jvmTest --tests "com.wire.kalium.logic.feature.auth.LoginUseCaseTest"

# Run single test method
./gradlew :logic:jvmTest --tests "com.wire.kalium.logic.feature.auth.LoginUseCaseTest.givenEmailHasLeadingOrTrailingSpaces*"

# iOS tests (requires Apple Silicon Mac and unified CoreCrypto)
./gradlew iosSimulatorArm64Test -PUSE_UNIFIED_CORE_CRYPTO=true
./gradlew :core:cryptography:iosSimulatorArm64Test -PUSE_UNIFIED_CORE_CRYPTO=true
./gradlew iOSOnlyAffectedTest -PUSE_UNIFIED_CORE_CRYPTO=true

# JavaScript tests (requires unified CoreCrypto)
./gradlew jsTest -PUSE_UNIFIED_CORE_CRYPTO=true

# Linting
./gradlew detekt

# Database migration verification
./gradlew :data:persistence:verifySqlDelightMigration

# Code coverage
./gradlew jvmTest koverXmlReport -Djava.library.path=./native/libs

# Run all unit tests and aggregate reports
./gradlew runAllUnitTests
./gradlew aggregateTestResults              # Creates combined HTML report
```

### iOS Framework Builds

```bash
# Build for iOS Simulator (Apple Silicon)
./gradlew :logic:linkDebugFrameworkIosSimulatorArm64 -PUSE_UNIFIED_CORE_CRYPTO=true

# Build for physical iOS devices
./gradlew :logic:linkDebugFrameworkIosArm64 -PUSE_UNIFIED_CORE_CRYPTO=true

# Build release frameworks
./gradlew :logic:linkReleaseFrameworkIosArm64 -PUSE_UNIFIED_CORE_CRYPTO=true
./gradlew :logic:linkReleaseFrameworkIosSimulatorArm64 -PUSE_UNIFIED_CORE_CRYPTO=true
```

**Note:** iOS and JS builds require `USE_UNIFIED_CORE_CRYPTO=true`. Either set it in gradle.properties or pass `-PUSE_UNIFIED_CORE_CRYPTO=true` on the command line.

### CLI Application

```bash
# Build JVM CLI
./gradlew :sample:cli:assemble
java -jar sample/cli/build/libs/cli.jar login --email <email> --password <password> listen-group

# Native CLI (macOS)
./gradlew :sample:cli:macosArm64Binaries   # ARM64
./gradlew :sample:cli:macosX64Binaries     # Intel
```

### Native Libraries

Native libraries (AVS) are required for JVM tests and CLI:

```bash
make                                    # Build from source, outputs to ./native/libs
```

Always pass `-Djava.library.path=./native/libs` when running JVM tests or CLI.

## Architecture

### Module Structure

Modules are organized by layer with colon-separated paths:

**Core (`core:*`):**
- `:core:common` - Shared data models and utilities
- `:core:data` - Data layer abstractions and DTOs
- `:core:cryptography` - Encryption using libsodium and CoreCrypto
- `:core:logger` - Logging infrastructure (Kermit-based)
- `:core:util` - General utilities, `Either<Failure, Success>` error handling

**Data (`data:*`):**
- `:data:network` - HTTP client (Ktor) with retry logic and authentication
- `:data:network-model` - Request/response models
- `:data:network-util` - Network utilities
- `:data:persistence` - SQLDelight database layer
- `:data:persistence-test` - Database test fixtures
- `:data:data-mappers` - Transformations between network/persistence/domain models
- `:data:protobuf` - Protocol Buffer definitions

**Domain (`domain:*`):**
- `:domain:backup` - Backup and restore with encryption
- `:domain:calling` - Voice/video calling via AVS library
- `:domain:cells` - Cell-based storage system (Wire Cells integration)
- `:domain:conversation-history` - Conversation history management
- `:domain:messaging:sending` - Message sending pipeline
- `:domain:messaging:receiving` - Message receiving and processing
- `:domain:work` - Background work management

**Logic:**
- `:logic` - Main SDK entry point, orchestrates all other modules, contains use cases

**Test (`test:*`):**
- `:test:mocks` - Network model mocks
- `:test:data-mocks` - Data layer mocks
- `:test:benchmarks` - JMH performance benchmarks
- `:test:tango-tests` - Integration tests

**Sample/Tools:**
- `:sample:cli` - Command-line application
- `:sample:samples` - Sample applications
- `:tools:backup-verification` - Backup integrity verification
- `:tools:protobuf-codegen` - Protobuf code generation

### Multiplatform Source Sets

Each module has platform-specific source sets:
- `commonMain/commonTest` - Shared Kotlin code
- `jvmMain/jvmTest` - JVM-specific code
- `androidMain/androidUnitTest` - Android-specific code
- `iosMain/iosTest` - iOS (partial support)
- `jsMain/jsTest -PUSE_UNIFIED_CORE_CRYPTO=true` - JavaScript (minimal support)

## Testing

**Frameworks:**
- Mockative for mocking (`@Mock` for interfaces, `@Mockable` for classes)
- Turbine for Flow testing (`test {}` blocks)
- Robolectric for Android unit tests
- Use `:data:persistence-test` fixtures for in-memory database testing

Place common tests in `commonTest` when possible.

## Code Conventions

- `suspend` functions for async, `Flow` for reactive streams
- `Either<Failure, Success>` pattern for error handling (from `:core:util`)
- `kotlinx-datetime` types (`Instant`, `LocalDateTime`) for dates
- `kotlinx.serialization` with `@Serializable` annotation
- Repository pattern in `:data:*`, use cases in `:logic`
- Constructor injection (no DI framework)
- Mappers in `:data:data-mappers` for model transformations

## Database

Two SQLDelight databases:
- **UserDatabase** (`db_user/`) - User-specific data (messages, conversations)
- **GlobalDatabase** (`db_global/`) - Shared data (accounts, server configuration)

Schema files: `data/persistence/src/commonMain/db_*/com/wire/kalium/persistence/*.sq`
Migrations: `data/persistence/src/commonMain/db_*/migrations/`

## Detekt Setup

Config: `detekt/detekt.yml`, Baseline: `detekt/baseline.xml`

IDE Setup: Settings → Tools → Detekt:
- Configuration Files: `$PROJECT_ROOT/detekt/detekt.yml`
- Baseline File: `$PROJECT_ROOT/detekt/baseline.xml`
- Plugin Jars: `$PROJECT_ROOT/detekt-rules/build/libs/detekt-rules.jar`

## Module Dependencies

The project follows a strict layered architecture:
- **Core** modules only depend on other core modules or external libraries
- **Data** modules depend on core modules
- **Domain** modules depend on core and data modules
- **Logic** depends on all layers and orchestrates them

The `:logic` module is the main SDK entry point that clients interact with.
