# Contributor Setup

This page covers local development tasks for Kalium contributors. The root README stays focused on SDK consumers.

## Requirements

- JDK 21
- Git
- macOS on Apple Silicon for iOS, macOS, and XCFramework builds
- Native libraries under `native/libs` for JVM tests and the CLI

Build native libraries with:

```bash
make
```

Always pass the native library path when running JVM tests or the CLI:

```bash
./gradlew jvmTest -Djava.library.path=./native/libs
```

## Pull Requests

For pull requests without a Jira ticket, add the `no-jira` label to skip Jira linking and fix-version validation.
The `[no-jira]` PR title marker is also supported. Adding or removing the label reruns the Jira check.

## Common Commands

```bash
# Clean build
./gradlew clean build

# JVM tests
./gradlew jvmTest -Djava.library.path=./native/libs

# Android unit tests
./gradlew testAndroidHostTest
./gradlew androidUnitOnlyAffectedTest
./gradlew :logic:testAndroidHostTest

# Single JVM test class
./gradlew :logic:jvmTest --tests "com.wire.kalium.logic.feature.auth.LoginUseCaseTest" -Djava.library.path=./native/libs

# Single JVM test method
./gradlew :logic:jvmTest --tests "com.wire.kalium.logic.feature.auth.LoginUseCaseTest.givenEmailHasLeadingOrTrailingSpaces*" -Djava.library.path=./native/libs

# iOS tests (see docs/IOS_BUILD.md for tests that need a booted simulator)
./gradlew :core:util:iosSimulatorArm64Test
./gradlew iOSOnlyAffectedTest

# JavaScript tests
./gradlew jsTest

# Lint
./gradlew detekt

# Database migration verification
./gradlew :data:persistence:jvmTest --tests com.wire.kalium.persistence.migrations.VerifyDatabaseMigrationsTest
./gradlew :data:persistence:verifySqlDelightMigration

# Coverage
./gradlew jvmTest koverXmlReport -Djava.library.path=./native/libs

# Aggregate unit-test reports
./gradlew runAllUnitTests
./gradlew aggregateTestResults

# Generate API documentation
make doc/generate-kdocs

# Generate and check navigation, visibility and resolved API signatures (requires Python 3)
make doc/test-kdocs
```

API documentation uses Dokka 2.2.0 and is written to `build/dokka/html`. Generate it on macOS
to resolve Apple platform types. This fork does not deploy API documentation from CI.
Affected common documentation sets in `cryptography`, `logic`, and `cli` use JVM-backed analysis,
retaining their declarations and display labels without changing SDK source sets or dependencies.
Persistence's intermediate Apple documentation sets use native target artifacts to resolve the SQLite bindings of Wire's SQLDelight fork (`com.wire.sqldelight`).

## Build Configuration

### `kalium.providerCacheScope`

Controls provider-level in-memory cache ownership. Consumer builds should set it explicitly; this repository uses `LOCAL` for standalone builds:

- `LOCAL`: each provider instance owns its cache map
- `GLOBAL`: provider instances share process-global cache maps

Current consumers:

- `UserStorageProvider`
- `UserAuthenticatedNetworkProvider`

Use the same flag for any new provider-level cache:

```bash
./gradlew <task> -Pkalium.providerCacheScope=LOCAL
```

## Testing Notes

- Prefer `commonTest` for multiplatform behavior.
- Use `jvmTest` for JVM-only behavior. Pass `-Djava.library.path=./native/libs`.
- Use `androidHostTest` for Android unit tests and Robolectric.
- Use `androidDeviceTest` for instrumented tests.
- Use `appleTest` for iOS/macOS coverage.
- Use `jsTest` only for modules with JavaScript support.

Test function names should follow `givenX_whenY_thenZ`:

```kotlin
fun givenEmailHasLeadingOrTrailingSpaces_whenLoggingIn_thenShouldBeTrimmed()
```

Reusable test infrastructure:

- `BaseDatabaseTest` and `GlobalDBBaseTest` in the `:data:persistence` test source sets
- `TestUserDatabase` and `TestGlobalDatabase` from `:data:persistence-test`
- `:test:data-mocks` for data-layer fixtures
- `:test:mocks` for network model mocks

Architectural fitness functions live in `logic/src/jvmTest/kotlin/com/wire/kalium/logic/architecture`:

```bash
./gradlew :logic:jvmTest --tests "*architecture*" -Djava.library.path=./native/libs
```

## Detekt In IntelliJ

Install the Detekt plugin and configure:

- Configuration file: `<project-root>/detekt/detekt.yml`
- Baseline file: `<project-root>/detekt/baseline.xml`
- Plugin jars: the `detekt-formatting`, `detekt-rules-libraries` and `com.wire:detekt-rules` artifacts listed in `buildSrc/src/main/kotlin/scripts/detekt.gradle.kts`

Or run Detekt from the terminal:

```bash
./gradlew detekt
```

## Contributor Rules Of Thumb

- Keep changes narrow and covered by tests.
- Run tests for every package you modify.
- Add a regression test when fixing a bug.
- Respect the dependency direction: `core -> data -> domain -> logic`.
- Do not expose `Either` types from `:logic`; wrap them in concrete public result types.
- Document architectural changes with an ADR under `docs/adr/`.
