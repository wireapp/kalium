package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyPackageManagerTests {

    @Test
    fun givenLastCheckWithinDuration_whenObservingAndSyncFinishes_refillKeyPackagesIsNotPerformed() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withLastKeyPackageCountCheck(Clock.System.now())
                .arrange()

            arrangement.syncRepository.updateSyncState { SyncState.Live }
            yield()
        }

    @Test
    fun givenLastCheckAfterDuration_whenObservingSyncFinishes_refillKeyPackagesIsPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withLastKeyPackageCountCheck(Clock.System.now() - KEY_PACKAGE_COUNT_CHECK_DURATION)
                .withRefillKeyPackagesUseCaseSuccessful()
                .withUpdateLastKeyPackageCountCheckSuccessful()
                .arrange()

            arrangement.syncRepository.updateSyncState { SyncState.Live }
            yield()

            verify(arrangement.refillKeyPackagesUseCase)
                .suspendFunction(arrangement.refillKeyPackagesUseCase::invoke)
                .wasInvoked(once)

            verify(arrangement.keyPackageRepository)
                .suspendFunction(arrangement.keyPackageRepository::updateLastKeyPackageCountCheck)
                .with(anything())
                .wasInvoked(once)
        }

    class Arrangement {

        internal val syncRepository: SyncRepository = InMemorySyncRepository()

        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val refillKeyPackagesUseCase = mock(classOf<RefillKeyPackagesUseCase>())

        fun withLastKeyPackageCountCheck(timestamp: Instant) = apply {
            given(keyPackageRepository)
                .suspendFunction(keyPackageRepository::lastKeyPackageCountCheck)
                .whenInvoked()
                .thenReturn(Either.Right(timestamp))
        }

        fun withRefillKeyPackagesUseCaseSuccessful() = apply {
            given(refillKeyPackagesUseCase)
                .suspendFunction(refillKeyPackagesUseCase::invoke)
                .whenInvoked()
                .thenReturn(RefillKeyPackagesResult.Success)
        }

        fun withUpdateLastKeyPackageCountCheckSuccessful() = apply {
            given(keyPackageRepository)
                .suspendFunction(keyPackageRepository::updateLastKeyPackageCountCheck)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to KeyPackageManagerImpl(
            syncRepository,
            lazy { keyPackageRepository },
            lazy { refillKeyPackagesUseCase },
            TestKaliumDispatcher
        )
    }
}
