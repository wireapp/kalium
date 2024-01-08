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
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

            verify(arrangement.refillKeyPackagesUseCase)
                .suspendFunction(arrangement.refillKeyPackagesUseCase::invoke)
                .wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingSyncFinishes_refillKeyPackagesIsNotPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verify(arrangement.refillKeyPackagesUseCase)
                .suspendFunction(arrangement.refillKeyPackagesUseCase::invoke)
                .wasNotInvoked()
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

            verify(arrangement.refillKeyPackagesUseCase)
                .suspendFunction(arrangement.refillKeyPackagesUseCase::invoke)
                .wasNotInvoked()
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

            verify(arrangement.refillKeyPackagesUseCase)
                .suspendFunction(arrangement.refillKeyPackagesUseCase::invoke)
                .wasInvoked(once)

            verify(arrangement.timestampKeyRepository)
                .suspendFunction(arrangement.timestampKeyRepository::reset)
                .with(anything())
                .wasInvoked(once)
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

            verify(arrangement.refillKeyPackagesUseCase)
                .suspendFunction(arrangement.refillKeyPackagesUseCase::invoke)
                .wasInvoked(once)

            verify(arrangement.timestampKeyRepository)
                .suspendFunction(arrangement.timestampKeyRepository::reset)
                .with(anything())
                .wasInvoked(once)
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val timestampKeyRepository = mock(classOf<TimestampKeyRepository>())

        @Mock
        val refillKeyPackagesUseCase = mock(classOf<RefillKeyPackagesUseCase>())

        @Mock
        val keyPackageCountUseCase = mock(classOf<MLSKeyPackageCountUseCase>())

        fun withLastKeyPackageCountCheck(hasPassed: Boolean) = apply {
            given(timestampKeyRepository)
                .suspendFunction(timestampKeyRepository::hasPassed)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(hasPassed))
        }

        fun withRefillKeyPackagesUseCaseSuccessful() = apply {
            given(refillKeyPackagesUseCase)
                .suspendFunction(refillKeyPackagesUseCase::invoke)
                .whenInvoked()
                .thenReturn(RefillKeyPackagesResult.Success)
        }

        fun withUpdateLastKeyPackageCountCheckSuccessful() = apply {
            given(timestampKeyRepository)
                .suspendFunction(timestampKeyRepository::reset)
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

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun withHasRegisteredMLSClient(result: Boolean) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(Either.Right(result))
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
