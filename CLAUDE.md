# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kalium is a Kotlin Multiplatform (KMP) messaging SDK for the Wire messaging platform. It handles end-to-end encryption, messaging protocols, voice/video calling, and backup functionality across JVM, Android, iOS, and JavaScript platforms.

**Key Technologies:**
- Kotlin 2.2.21 with Kotlin Multiplatform
- Gradle with Kotlin DSL
- SQLDelight 2.0.1 for database layer
- Ktor 3.3.2 for HTTP networking
- CoreCrypto 9.1.1 + libsodium for cryptography
- AVS 10.1.32 for audio/video calling
- Protocol Buffers for serialization

## Build Commands

### Basic Build & Test

```bash
# Clean build
./gradlew clean build

# JVM tests (requires native libraries)
./gradlew jvmTest -Djava.library.path=./native/libs

# Android unit tests (only affected modules)
./gradlew androidUnitOnlyAffectedTest

# Run all unit tests across all modules
./gradlew runAllUnitTests

# Aggregate test results into HTML report
./gradlew aggregateTestResults

# Code coverage report
./gradlew jvmTest koverXmlReport -Djava.library.path=./native/libs
```

### Code Quality

```bash
# Run Detekt linting
./gradlew detekt

# Or via Makefile
make detekt/run-verify

# Verify database migrations
./gradlew :persistence:verifySqlDelightMigration
make db/verify-all-migrations
```

### CLI Application

```bash
# Build JVM CLI
./gradlew :cli:assemble
java -jar cli/build/libs/cli.jar login --email <email> --password <password> listen-group

# Build native CLI (macOS ARM64)
./gradlew :cli:macosArm64Binaries
./cli/build/bin/macosArm64/debugExecutable/cli.kexe login

# Build native CLI (macOS Intel)
./gradlew :cli:macosX64Binaries
./cli/build/bin/macosX64/debugExecutable/cli.kexe login
```

### Documentation

```bash
# Generate API documentation
./gradlew dokkaHtmlMultiModule
```

### Native Libraries

Native libraries (libsodium, cryptobox-c, cryptobox4j) are required for cryptography operations. On macOS 12:

```bash
# Build from source
make

# Libraries output to: ./native/libs
```

When running tasks requiring native libraries, pass: `-Djava.library.path=./native/libs`

## Architecture

### Module Structure

Kalium uses a modular architecture with 28+ modules organized by functionality:

**Core SDK:**
- `:logic` - Main SDK entry point, orchestrates all other modules
- `:common` - Shared data models and utilities
- `:data` - Data layer abstractions and DTOs
- `:data-mappers` - Transformations between network/persistence/domain models

**Communication:**
- `:network` - HTTP client (Ktor-based) with retry logic and authentication
- `:network-model` - Request/response models
- `:network-util` - Network utilities (connectivity, error handling)
- `:messaging:sending` - Message sending pipeline
- `:messaging:receiving` - Message receiving and processing

**Persistence:**
- `:persistence` - SQLDelight database layer with SQLCipher encryption
- `:persistence-test` - Test fixtures for database testing
- `:conversation-history` - Conversation history management

**Security:**
- `:cryptography` - Encryption/decryption using libsodium and CoreCrypto
- `:backup` - Backup and restore with encryption
- `:backup-verification` - Backup integrity verification

**Other:**
- `:calling` - Voice/video calling via AVS library
- `:cells` - Cell-based storage system
- `:protobuf` - Protocol Buffer definitions
- `:logger` - Logging infrastructure (Kermit-based)
- `:util` - General utilities
- `:cli` - Command-line application
- `:samples` - Sample applications
- `:monkeys` - Test servers (Docker-based)
- `:benchmarks` - JMH performance benchmarks

### Dependency Flow

The `:logic` module is the main SDK entry point and depends on most other modules. Key dependencies:

```
:logic → :common → :data → :network-model
       → :network → :network-util
       → :cryptography
       → :persistence
       → :calling
       → :backup
```

All modules depend on `:logger` and most on `:util` for shared functionality.

### Multiplatform Structure

Each module typically has platform-specific source sets:
- `commonMain/commonTest` - Shared Kotlin code
- `jvmMain/jvmTest` - JVM-specific code
- `androidMain/androidUnitTest` - Android-specific code
- `iosMain/iosTest` - iOS-specific code (partial support)
- `jsMain/jsTest` - JavaScript code (minimal support)

## Testing

### Frameworks & Tools
- JUnit 5 (JUnitPlatform) for test structure
- Mockative 3.1.4 for mocking (preferred over Mockk)
- Turbine 1.1.0 for Flow testing
- Robolectric 4.12.1 for Android unit tests
- Kotest for assertions

### Writing Tests
- Place common tests in `commonTest` when possible
- Use Mockative annotations: `@Mock` for interfaces, mark classes as `@Mockable` in production code
- For Android-specific tests requiring Android framework, use `androidUnitTest` with Robolectric
- Flow testing: Use Turbine's `test {}` blocks for collecting emissions
- Database tests: Use `:persistence-test` fixtures for in-memory database testing

### Running Specific Tests

```bash
# Single module JVM tests
./gradlew :logic:jvmTest -Djava.library.path=./native/libs

# Single module Android tests
./gradlew :logic:testDebugUnitTest

# Single test class (example)
./gradlew :logic:jvmTest --tests "com.wire.kalium.logic.feature.auth.LoginUseCaseTest"
```

## Code Style & Guidelines

### Detekt Configuration
- Config: `detekt/detekt.yml`
- Baseline: `detekt/baseline.xml`
- Custom Wire rules via `com.wire:detekt-rules:1.0.0-1.23.6`

**IDE Setup:**
1. Install Detekt plugin in IntelliJ
2. Settings → Tools → Detekt:
   - Configuration Files: `$PROJECT_ROOT/detekt/detekt.yml`
   - Baseline File: `$PROJECT_ROOT/detekt/baseline.yml`
   - Plugin Jars: `$PROJECT_ROOT/detekt-rules/build/libs/detekt-rules.jar`

### Conventions
- Use Kotlin coroutines with `suspend` functions for async operations
- Prefer `Flow` over `Channel` for reactive streams
- Use `Either<Failure, Success>` pattern for error handling (from `:util`)
- Database queries return `Flow` for reactive updates
- Network calls use Ktor's suspend-based API
- All dates/times use `kotlinx-datetime` types (`Instant`, `LocalDateTime`)
- Serialization uses `kotlinx.serialization` with `@Serializable` annotation

### Architecture Patterns
- Repository pattern: `:data` layer provides repositories, `:logic` contains use cases
- Dependency injection: Constructor injection, no DI framework in library (consumers provide DI)
- Use cases: Single-responsibility classes in `:logic` that orchestrate business logic
- Mappers: Dedicated mapper objects in `:data-mappers` for model transformations
- Event-driven: Key events flow through `EventGatherer` and `EventProcessor`

## Important Notes

### Native Library Requirements
Many tests and features require native cryptography libraries. Always include `-Djava.library.path=./native/libs` when running JVM tests or CLI commands that involve encryption, calling, or protocol buffer operations.

### Database Migrations
When modifying SQLDelight schemas:
1. Add migration SQL in `persistence/src/commonMain/sqldelight/migrations/`
2. Verify migration: `./gradlew :persistence:verifySqlDelightMigration`
3. Update schema version in `.sq` files

### Protocol Buffers
Protobuf definitions are in `:protobuf/src/commonMain/proto/`. After modifying:
1. Rebuild: `./gradlew :protobuf:generateProto`
2. Regenerated Kotlin code appears in `build/generated/source/proto/`

### CI/CD
GitHub Actions workflows run on PR and push to `develop`:
- `gradle-jvm-tests.yml` - Main test pipeline (JVM + JS)
- `gradle-android-unit-tests.yml` - Android tests
- `codestyle.yml` - Detekt validation

Tests run in Docker container `kubazwire/cryptobox:1.0.0` which includes native dependencies.

### Multiplatform Considerations
- When adding new dependencies, check compatibility with all platforms (especially iOS/JS)
- Platform-specific code goes in `*Main/*Test` source sets
- Use `expect/actual` mechanism for platform-specific implementations
- Not all modules support all platforms - check `build.gradle.kts` for target configuration

### AVS Calling Library
The `:calling` module wraps the proprietary AVS (Audio Video Signaling) library v10.1.32. This is a native library for voice/video calling. Updates require coordination with AVS team.

### CoreCrypto
E2E encryption uses CoreCrypto library v9.1.1 (Rust-based, exposed via JNI/C interop). This handles MLS (Messaging Layer Security) protocol operations.
