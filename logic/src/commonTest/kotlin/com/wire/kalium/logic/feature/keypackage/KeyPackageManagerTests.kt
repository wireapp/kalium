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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

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

            coVerify {
                arrangement.refillKeyPackagesUseCase.invoke()
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingSyncFinishes_refillKeyPackagesIsNotPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            coVerify {
                arrangement.refillKeyPackagesUseCase.invoke()
            }.wasNotInvoked()
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

            coVerify {
                arrangement.refillKeyPackagesUseCase.invoke()
            }.wasNotInvoked()
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

            coVerify {
                arrangement.refillKeyPackagesUseCase.invoke()
            }.wasInvoked(once)

            coVerify {
                arrangement.timestampKeyRepository.reset(any())
            }.wasInvoked(once)
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

            coVerify {
                arrangement.refillKeyPackagesUseCase.invoke()
            }.wasInvoked(once)

            coVerify {
                arrangement.timestampKeyRepository.reset(any())
            }.wasInvoked(once)
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()
        val clientRepository = mock(ClientRepository::class)
        val featureSupport = mock(FeatureSupport::class)
        val timestampKeyRepository = mock(TimestampKeyRepository::class)
        val refillKeyPackagesUseCase = mock(RefillKeyPackagesUseCase::class)
        val keyPackageCountUseCase = mock(MLSKeyPackageCountUseCase::class)

        suspend fun withLastKeyPackageCountCheck(hasPassed: Boolean) = apply {
            coEvery {
                timestampKeyRepository.hasPassed(any(), any())
            }.returns(Either.Right(hasPassed))
        }

        suspend fun withRefillKeyPackagesUseCaseSuccessful() = apply {
            coEvery {
                refillKeyPackagesUseCase.invoke()
            }.returns(RefillKeyPackagesResult.Success)
        }

        suspend fun withUpdateLastKeyPackageCountCheckSuccessful() = apply {
            coEvery {
                timestampKeyRepository.reset(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withKeyPackageCountReturnsRefillTrue() = apply {
            coEvery {
                keyPackageCountUseCase.invoke(any())
            }.returns(
                MLSKeyPackageCountResult.Success(
                    TestClient.CLIENT_ID,
                    0, true
                )
            )
        }

        suspend fun withKeyPackageCountFailed() = apply {
            coEvery {
                keyPackageCountUseCase.invoke(any())
            }.returns(MLSKeyPackageCountResult.Failure.Generic(CoreFailure.MissingClientRegistration))
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(supported)
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            coEvery {
                clientRepository.hasRegisteredMLSClient()
            }.returns(Either.Right(result))
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
