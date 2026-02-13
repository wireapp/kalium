# Mockative to Mokkery Migration Guide

This guide captures the migration pattern used in this repository when moving tests from Mockative to Mokkery.

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

Then run broader checks:

```bash
./gradlew --no-daemon :logic:jvmTest --tests "*UserPropertyRepositoryTest" --tests "*PersistScreenshotCensoringConfigUseCaseTest" --tests "*SyncUserPropertiesUseCaseTest" --tests "*SlowSyncWorkerTest"
./gradlew --no-daemon detekt
```

