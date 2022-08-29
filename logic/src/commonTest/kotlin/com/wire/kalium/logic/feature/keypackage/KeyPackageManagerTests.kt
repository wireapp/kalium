package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.framework.TestClient
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
    fun givenLastCheckWithinDurationAndMLSValidPackageCountFailed_whenObservingAndSyncFinishes_refillKeyPackagesIsNotPerformed() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withLastKeyPackageCountCheck(Clock.System.now())
                .withKeyPackageCountFailed()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()
        }

    @Test
    fun givenLastCheckAfterDuration_whenObservingSyncFinishes_refillKeyPackagesIsPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withLastKeyPackageCountCheck(Clock.System.now() - KEY_PACKAGE_COUNT_CHECK_DURATION)
                .withRefillKeyPackagesUseCaseSuccessful()
                .withKeyPackageCountFailed()
                .withUpdateLastKeyPackageCountCheckSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verify(arrangement.refillKeyPackagesUseCase)
                .suspendFunction(arrangement.refillKeyPackagesUseCase::invoke)
                .wasInvoked(once)

            verify(arrangement.keyPackageRepository)
                .suspendFunction(arrangement.keyPackageRepository::updateLastKeyPackageCountCheck)
                .with(anything())
                .wasInvoked(once)
        }

    @Test
    fun givenLastCheckBeforeDuration_whenKeyPackageCountsReturnRefillTrue_refillKeyPackagesIsPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withLastKeyPackageCountCheck(Clock.System.now())
                .withRefillKeyPackagesUseCaseSuccessful()
                .withKeyPackageCountReturnsRefillTrue()
                .withUpdateLastKeyPackageCountCheckSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verify(arrangement.refillKeyPackagesUseCase)
                .suspendFunction(arrangement.refillKeyPackagesUseCase::invoke)
                .wasInvoked(once)

            verify(arrangement.keyPackageRepository)
                .suspendFunction(arrangement.keyPackageRepository::updateLastKeyPackageCountCheck)
                .with(anything())
                .wasInvoked(once)
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val refillKeyPackagesUseCase = mock(classOf<RefillKeyPackagesUseCase>())

        @Mock
        val keyPackageCountUseCase = mock(classOf<MLSKeyPackageCountUseCase>())

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

        fun withKeyPackageCountReturnsRefillTrue() = apply {
            given(keyPackageCountUseCase)
                .suspendFunction(keyPackageCountUseCase::invoke)
                .whenInvokedWith(anything())
                .thenReturn(
                    MLSKeyPackageCountResult.Success(
                        TestClient.CLIENT_ID,
                        0, true
                    )
                )
        }

        fun withKeyPackageCountFailed() = apply {
            given(keyPackageCountUseCase)
                .suspendFunction(keyPackageCountUseCase::invoke)
                .whenInvokedWith(anything())
                .thenReturn(MLSKeyPackageCountResult.Failure.Generic(CoreFailure.MissingClientRegistration))
        }

        fun arrange() = this to KeyPackageManagerImpl(
            incrementalSyncRepository,
            lazy { keyPackageRepository },
            lazy { refillKeyPackagesUseCase },
            lazy { keyPackageCountUseCase },
            TestKaliumDispatcher
        )
    }
}
