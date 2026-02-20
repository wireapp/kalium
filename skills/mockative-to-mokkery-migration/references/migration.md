# Mockative to Mokkery Migration Guide

This guide captures the migration pattern used in this repository when moving tests from Mockative to Mokkery.

## 0. Resolve scope to real paths first

User-provided scope may use shorthand package names (for example `logic/cache`) that are not real top-level directories.

Before editing:

- resolve to actual paths under `logic/src/*Test/kotlin/com/wire/kalium/logic/...`
- verify target files still contain Mockative usage

Helpful commands:

```bash
find logic/src -type d \( -name cache -o -name client -o -name configuration -o -name corefailure \) | sort
rg -n "io\\.mockative|coEvery|coVerify|verify\\s*\\{|every\\s*\\{" logic/src/commonTest/kotlin/com/wire/kalium/logic/cache logic/src/commonTest/kotlin/com/wire/kalium/logic/client logic/src/commonTest/kotlin/com/wire/kalium/logic/configuration logic/src/commonTest/kotlin/com/wire/kalium/logic/corefailure
```

## 1. Decide interface strategy first

For each mocked interface in `commonMain`:

- Keep `@Mockable` if you still need Mockative-generated mocks for that type.
- Remove `@Mockable` (and `io.mockative.Mockable` import) if the type is fully migrated to Mokkery.

Important:

- If you remove `@Mockable`, any remaining Mockative tests for that type will fail with `NoSuchMockException`.

## 2. Replace test imports

Replace Mockative imports:

```kotlin
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
```

With Mokkery imports:

```kotlin
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
```

## 3. Replace mock creation

Mockative:

```kotlin
val repository = mock(UserPropertyRepository::class)
```

Mokkery:

```kotlin
val repository: UserPropertyRepository = mock<UserPropertyRepository>()
```

If the collaborator has many unstubbed `Unit` methods (common for DAOs), prefer:

```kotlin
val dao = mock<SomeDao>(mode = MockMode.autoUnit)
```

Otherwise Mokkery may fail with `CallNotMockedException` for unit-returning calls such as `insert(...)` or `update(...)`.

## 4. Replace stubbing

Mockative:

```kotlin
coEvery { repository.syncPropertiesStatuses() }.returns(Either.Right(Unit))
```

Mokkery:

```kotlin
everySuspend { repository.syncPropertiesStatuses() }.returns(Either.Right(Unit))
```

Also valid:

```kotlin
everySuspend { repository.syncPropertiesStatuses() } returns Either.Right(Unit)
```

## 5. Replace verification

Mockative:

```kotlin
coVerify { repository.syncPropertiesStatuses() }.wasInvoked(exactly = once)
coVerify { repository.syncPropertiesStatuses() }.wasNotInvoked()
```

Mokkery:

```kotlin
verifySuspend { repository.syncPropertiesStatuses() }
verifySuspend(VerifyMode.not) { repository.syncPropertiesStatuses() }
verifySuspend(VerifyMode.exactly(0)) { repository.syncPropertiesStatuses() }
verifySuspend(VerifyMode.exactly(1)) { repository.syncPropertiesStatuses() }
```

For non-suspend methods, use `verify { ... }` with the same `VerifyMode` patterns.

## 6. Mixed-framework test files

If a test file still uses both libraries during incremental migration:

- Keep existing Mockative usage for untouched dependencies.
- Use Mokkery only for migrated dependency types.
- Alias imports to avoid `mock` naming clashes:

```kotlin
import dev.mokkery.mock as mokkeryMock
```

Then:

```kotlin
val migratedDep: SyncUserPropertiesUseCase = mokkeryMock<SyncUserPropertiesUseCase>()
```

## 7. Find remaining migration surface

Helpful commands:

```bash
rg -n "io\\.mockative|@Mockable" logic/src/commonMain/kotlin logic/src/commonTest/kotlin
rg -n "SyncUserPropertiesUseCase" logic/src/commonTest/kotlin
```

## 8. Validate

Run focused tests for changed areas first:

```bash
./gradlew --no-daemon :logic:jvmTest --tests "*SyncUserPropertiesUseCaseTest" --tests "*SlowSyncWorkerTest"
```

In this repository, `:logic:commonTest` is not an available task; use `:logic:jvmTest` for these common test classes.
If unsure, inspect task names first:

```bash
./gradlew :logic:tasks --all
```

Then run broader checks:

```bash
./gradlew --no-daemon :logic:jvmTest --tests "*UserPropertyRepositoryTest" --tests "*PersistScreenshotCensoringConfigUseCaseTest" --tests "*SyncUserPropertiesUseCaseTest" --tests "*SlowSyncWorkerTest"
./gradlew --no-daemon detekt
```

## 9. Split-by-folder delivery (when requested)

If the user asks for smaller PRs:

- migrate and validate one folder at a time
- create one branch and one commit per folder
- open one PR per folder targeting `develop`
- if a folder contains no Mockative usage, report no-op explicitly and create an empty commit only if the user requested a branch/PR for that folder
