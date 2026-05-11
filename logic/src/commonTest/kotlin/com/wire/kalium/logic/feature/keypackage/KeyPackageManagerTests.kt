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
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

            verifySuspend(VerifyMode.not) {
                arrangement.refillKeyPackagesUseCase.invoke(any())
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
                arrangement.refillKeyPackagesUseCase.invoke(any())
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
                arrangement.refillKeyPackagesUseCase.invoke(any())
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
                arrangement.refillKeyPackagesUseCase.invoke(any())
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
                arrangement.refillKeyPackagesUseCase.invoke(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.timestampKeyRepository.reset(any())
            }
        }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

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
                refillKeyPackagesUseCase.invoke(any())
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

        suspend fun arrange() = this to KeyPackageManagerImpl(
            featureSupport,
            incrementalSyncRepository,
            lazy { clientRepository },
            lazy { refillKeyPackagesUseCase },
            lazy { keyPackageCountUseCase },
            lazy { timestampKeyRepository },
            cryptoTransactionProvider,
            TestKaliumDispatcher
        ).also {
            withMLSTransactionReturning(Either.Right(Unit))
        }
    }
}
