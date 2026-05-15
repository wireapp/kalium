# AGENTS.md

This file provides guidance to Agents when working with code in this repository.
See https://agents.md/ for details about this file type.

## Project Overview

Kalium is a Kotlin Multiplatform (KMP) messaging SDK for the Wire messaging platform. It handles end-to-end encryption, messaging protocols, voice/video calling, and backup functionality across JVM, Android, iOS, and JavaScript platforms.

**Requirements:** JDK 21, Git, macOS Apple Silicon for iOS builds

**Key Technologies:**
- Kotlin 2.3.0 with Kotlin Multiplatform
- Gradle with Kotlin DSL
- SQLDelight 2.2.1 for database layer (SQLCipher encrypted)
- Ktor 3.4.0 for HTTP networking
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
./gradlew :data:persistence:jvmTest --tests com.wire.kalium.persistence.migrations.VerifyDatabaseMigrationsTest
./gradlew :data:persistence:verifySqlDelightMigration # Use this when we upgrade SQLDelight to avoid hanging on the test task

# Code coverage
./gradlew jvmTest koverXmlReport -Djava.library.path=./native/libs

# Run all unit tests and aggregate reports
./gradlew runAllUnitTests
./gradlew aggregateTestResults              # Creates combined HTML report
```

### iOS Builds

See [docs/IOS_BUILD.md](docs/IOS_BUILD.md) for comprehensive iOS build documentation.

**Requirements:** macOS Apple Silicon (Intel not supported), Xcode with command-line tools

**Supported targets:**
- `iosArm64` - Physical iOS devices (iPhone, iPad)
- `iosSimulatorArm64` - iOS Simulator on Apple Silicon Macs
- `macosArm64` - macOS on Apple Silicon

```bash
# iOS tests (requires Apple Silicon Mac and unified CoreCrypto)
./gradlew iosSimulatorArm64Test -PUSE_UNIFIED_CORE_CRYPTO=true
./gradlew iOSOnlyAffectedTest -PUSE_UNIFIED_CORE_CRYPTO=true

# iOS framework builds — see docs/IOS_BUILD.md for full details
./gradlew :logic:linkDebugFrameworkIosSimulatorArm64 -PUSE_UNIFIED_CORE_CRYPTO=true
```

User provider cache mode can be controlled at compile time with:
- `kalium.providerCacheScope` is required and has no Kalium default; consumer builds must set it explicitly
- `kalium.providerCacheScope=LOCAL`: each provider instance owns a local cache map
- `kalium.providerCacheScope=GLOBAL`: provider instances share process-global cache maps
- Current consumers: `UserStorageProvider` and `UserAuthenticatedNetworkProvider`
- Extension rule: new provider-level caches should reuse this policy flag

CLI override examples:
- `./gradlew <task> -Pkalium.providerCacheScope=LOCAL|GLOBAL`

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
- `:core:common` - Shared data models, utilities, and `Either<Failure, Success>` error handling
- `:core:data` - Data layer abstractions and DTOs
- `:core:cryptography` - Encryption using libsodium and CoreCrypto
- `:core:libsodium` - Libsodium bindings wrapper for cryptographic primitives
- `:core:logger` - Logging infrastructure (Kermit-based)
- `:core:util` - General utilities

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
- `:domain:nomaddevice` - Nomad device session management (forced logout, data wipe on expiry)
- `:domain:usernetwork` - User network configuration
- `:domain:userstorage` - User storage management
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
- `jsMain/jsTest` - JavaScript (minimal support)

### Database

Two SQLDelight databases:
- **UserDatabase** (`db_user/`) - User-specific data (messages, conversations)
- **GlobalDatabase** (`db_global/`) - Shared data (accounts, server configuration)

Schema files: `data/persistence/src/commonMain/db_*/com/wire/kalium/persistence/*.sq`
Migrations: `data/persistence/src/commonMain/db_*/migrations/`

## Module Dependencies

The project follows a strict layered architecture:
- **Core** modules only depend on other core modules or external libraries
- **Data** modules depend on core modules
- **Domain** modules depend on core and data modules
- **Logic** depends on all layers and orchestrates them

The `:logic` module is the main SDK entry point that clients interact with.

## Testing

**Frameworks:**
- Mokkery for mocking (`mock<SomeClass>()`)
- Turbine for Flow testing (`test {}` blocks)
- Robolectric for Android unit tests
- Use `:data:persistence-test` fixtures for in-memory database testing
- Architectural fitness functions tests are located in `logic/src/jvmTest/kotlin/com/wire/kalium/logic/architecture` and can be run or extended

**Test naming convention:** 

Use `givenX_whenY_thenZ` for all test function names:
```kotlin
fun givenEmailHasLeadingOrTrailingSpaces_whenLoggingIn_thenShouldBeTrimmed()
```

**Platform test source sets:**
- `commonTest` — preferred for multiplatform tests
- `jvmTest` — JVM-only (requires `-Djava.library.path=./native/libs`)
- `androidHostTest` — Android unit tests (Robolectric)
- `androidDeviceTest` — Android instrumented tests
- `appleTest` — iOS/macOS tests
- `jsTest` — JavaScript tests

**Reusable test infrastructure:**
- `GlobalDBBaseTest` / `BaseDatabaseTest` in `:data:persistence-test` — use for database tests (in-memory DB setup)
- `:test:data-mocks` — mock factories for users, conversations, messages

**Architectural tests:** Konsist fitness functions enforce layer boundaries and use case patterns:
- Located at: `logic/src/jvmTest/kotlin/com/wire/kalium/logic/architecture/`
- Run with: `./gradlew :logic:jvmTest --tests "*architecture*"`

## Code Conventions

- `suspend` functions for async, `Flow` for reactive streams
- `Either<Failure, Success>` pattern for error handling (from `:core:common`)
- `kotlinx-datetime` types (`Instant`, `LocalDateTime`) for dates
- `kotlinx.serialization` with `@Serializable` annotation
- Repository pattern in `:data:*`, use cases in `:logic`
- Constructor injection (no DI framework)
- Mappers in `:data:data-mappers` for model transformations
- `:logic` exposes concrete types and does not expose `Either` types

## Common Pitfalls

- **JVM tests fail silently** without `-Djava.library.path=./native/libs`
- **iOS/JS builds fail** without `-PUSE_UNIFIED_CORE_CRYPTO=true`
- **`kalium.providerCacheScope` has no default** — consumers must explicitly set `LOCAL` or `GLOBAL`
- **`:logic` must NOT expose `Either<>` types** to callers — wrap results in concrete types
- **Mokkery only:** use `mock<SomeClass>()` for test mocks
- **`shadowJar` service discovery:** requires `duplicatesStrategy = DuplicatesStrategy.INCLUDE` before `mergeServiceFiles()` in `tools/testservice/build.gradle.kts`

## Security Guidelines and Permissions

- Never read secrets in the codebase.
    - API keys, passwords, tokens, should always be ignored and not processed.
- Allowed Without Prompting:
    - Read any source file.
    - Run linters, formatters, type checkers on single files.
    - Run unit tests on specific test files.
- Require Approval First:
    - Adding a new library/dependency.
    - Changing the dependencies between modules.
    - Git operations (`git push`, `git commit`).
    - Deleting files or directories.
    - Running full build or E2E tests.
    - Modifying CI/CD configuration and scripts.
    - Introducing a new architectural pattern or design convention.

## Agent Commandments

Adhere to the following guidelines for each session:

### 1. Write code that can be tested
- If the code is not possible to test, then it is not a valid solution.

### 2. All tests of changed packages must be green
- Run `./gradlew :<module>:jvmTest` for each package you modified before finishing.
- All new and modified code paths must be covered by tests.
- When fixing a bug, add a regression test.

### 3. Follow project patterns
- **Use cases:** define a functional interface in `:logic`, impl in the same package, injected via constructor.
- **Data flow:** Network model → Repository → Mapper → Use case → exposed via `UserSession`/`GlobalKaliumScope`.
- **Error handling:** Use `Either<CoreFailure, T>` in data/domain layers. `:logic` exposes concrete return types to callers — never `Either`.
- **Constructor injection** — no DI framework; pass all dependencies at construction time.

### 4. Respect module boundaries
- Dependency direction: `core → data → domain → logic`. Never invert.
- Do not cross module boundaries without checking existing dependency rules.
- See `docs/adr/` for architectural decision records before making structural changes.

### 5. Document architectural changes with an ADR
- Adding a new library/dependency or introducing a new pattern requires an ADR in `docs/adr/`.
- Name it sequentially: `docs/adr/XXXX-kebab-case-title.md` (see `0000-template-lightway-adr.md` for the template).
- Get the ADR approved before implementing the change.

### 6. Limit scope and ask when uncertain
- Focus on narrow, well-defined tasks.
- **Require approval before:** adding a dependency, changing module dependencies, introducing a new pattern, touching CI/CD, deleting files, running full builds.

### 7. Run linter before finishing
- `./gradlew detekt` — must pass on all changed files.
