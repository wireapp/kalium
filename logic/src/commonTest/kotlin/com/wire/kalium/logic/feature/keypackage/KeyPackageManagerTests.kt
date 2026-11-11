/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyPackageManagerTests {

    @Test
    fun givenLastCheckWithinDurationAndMLSValidPackageCountFailed_whenObservingAndSyncFinishes_refillKeyPackagesIsNotPerformed() =
        runTest(TestKaliumDispatcher.default) {

            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .withLastKeyPackageCountCheck(false)
                .withKeyPackageCountFailed()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.refillKeyPackagesUseCase.invoke()
            }
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingSyncFinishes_refillKeyPackagesIsNotPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.refillKeyPackagesUseCase.invoke()
            }
        }

    @Test
    fun givenNoMLSClientIsRegistered_whenObservingSyncFinishes_refillKeyPackagesIsNotPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.refillKeyPackagesUseCase.invoke()
            }
        }

    @Test
    fun givenLastCheckAfterDuration_whenObservingSyncFinishes_refillKeyPackagesIsPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withLastKeyPackageCountCheck(true)
                .withRefillKeyPackagesUseCaseSuccessful()
                .withKeyPackageCountFailed()
                .withUpdateLastKeyPackageCountCheckSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.refillKeyPackagesUseCase.invoke()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.timestampKeyRepository.reset(any())
            }
        }

    @Test
    fun givenLastCheckBeforeDuration_whenKeyPackageCountsReturnRefillTrue_refillKeyPackagesIsPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withLastKeyPackageCountCheck(false)
                .withRefillKeyPackagesUseCaseSuccessful()
                .withKeyPackageCountReturnsRefillTrue()
                .withUpdateLastKeyPackageCountCheckSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.refillKeyPackagesUseCase.invoke()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.timestampKeyRepository.reset(any())
            }
        }

    @Test
    fun whenRefillIsScheduled_thenRefillRunsWithoutWaitingForTheHourlyCheck() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, manager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withRefillKeyPackagesUseCaseSuccessful()
                .withUpdateLastKeyPackageCountCheckSuccessful()
                .arrange()

            manager.scheduleRefill()
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.refillKeyPackagesUseCase.invoke()
            }
            verifySuspend(VerifyMode.not) {
                arrangement.timestampKeyRepository.hasPassed(any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.keyPackageCountUseCase.invoke(any())
            }
        }

    @Test
    fun givenNoMLSClientIsRegistered_whenRefillIsScheduled_thenRefillIsNotPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, manager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .arrange()

            manager.scheduleRefill()
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.refillKeyPackagesUseCase.invoke()
            }
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun givenRefillIsRunning_whenAnotherRefillIsScheduled_thenItWaitsForTheFirstOneToFinish() =
        runTest(TestKaliumDispatcher.default) {
            val firstRefillCanFinish = CompletableDeferred<Unit>()
            var started = 0
            var running = 0
            var maxRunning = 0
            val (arrangement, manager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withUpdateLastKeyPackageCountCheckSuccessful()
                .arrange()
            everySuspend { arrangement.refillKeyPackagesUseCase.invoke() } calls {
                started++
                running++
                maxRunning = maxOf(maxRunning, running)
                firstRefillCanFinish.await()
                running--
                RefillKeyPackagesResult.Success
            }

            manager.scheduleRefill()
            manager.scheduleRefill()
            advanceUntilIdle()

            assertEquals(1, started)

            firstRefillCanFinish.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, started)
            assertEquals(1, maxRunning)
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()
        val clientRepository: ClientRepository = mock()
        val featureSupport: FeatureSupport = mock()
        val timestampKeyRepository: TimestampKeyRepository = mock()
        val refillKeyPackagesUseCase: RefillKeyPackagesUseCase = mock()
        val keyPackageCountUseCase: MLSKeyPackageCountUseCase = mock()

        suspend fun withLastKeyPackageCountCheck(hasPassed: Boolean) = apply {
            everySuspend {
                timestampKeyRepository.hasPassed(any(), any())
            } returns Either.Right(hasPassed)
        }

        suspend fun withRefillKeyPackagesUseCaseSuccessful() = apply {
            everySuspend {
                refillKeyPackagesUseCase.invoke()
            } returns RefillKeyPackagesResult.Success
        }

        suspend fun withUpdateLastKeyPackageCountCheckSuccessful() = apply {
            everySuspend {
                timestampKeyRepository.reset(any())
            } returns Either.Right(Unit)
        }

        suspend fun withKeyPackageCountReturnsRefillTrue() = apply {
            everySuspend {
                keyPackageCountUseCase.invoke(any())
            } returns
                MLSKeyPackageCountResult.Success(
                    TestClient.CLIENT_ID,
                    0, true
                )
        }

        suspend fun withKeyPackageCountFailed() = apply {
            everySuspend {
                keyPackageCountUseCase.invoke(any())
            } returns MLSKeyPackageCountResult.Failure.Generic(CoreFailure.MissingClientRegistration)
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            } returns supported
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            everySuspend {
                clientRepository.hasRegisteredMLSClient()
            } returns Either.Right(result)
        }

        fun arrange() = this to KeyPackageManagerImpl(
            featureSupport,
            incrementalSyncRepository,
            lazy { clientRepository },
            lazy { refillKeyPackagesUseCase },
            lazy { keyPackageCountUseCase },
            lazy { timestampKeyRepository },
            TestKaliumDispatcher
        )
    }
}
