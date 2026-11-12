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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackagePool
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import dev.mokkery.answering.returns
import dev.mokkery.MockMode
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RefillKeyPackageUseCaseTest {

    @Test
    fun givenRefillKeyPackageUseCase_WhenNeedRefillReturnTrue_ThenRequestToRefillKeyPackageIsPerformed() = runTest {
        val keyPackageCount = (Arrangement.KEY_PACKAGE_LIMIT * Arrangement.KEY_PACKAGE_THRESHOLD - 1).toInt()

        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackagePool(Arrangement.KEY_PACKAGE_LIMIT)
            .withKeyPackageCount(keyPackageCount)
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageRepository.uploadNewKeyPackages(
                any(),
                eq(TestClient.CLIENT_ID),
                eq(Arrangement.KEY_PACKAGE_LIMIT - keyPackageCount)
            )
        }

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenRefillKeyPackageUseCase_WhenNeedRefillReturnFalse_ThenRequestToRefillKeyPackageIsPerformed() = runTest {
        val keyPackageCount = (Arrangement.KEY_PACKAGE_LIMIT * Arrangement.KEY_PACKAGE_THRESHOLD).toInt()

        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackagePool(Arrangement.KEY_PACKAGE_LIMIT)
            .withKeyPackageCount(keyPackageCount)
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenErrorIsEncountered_ThenFailureIsPropagated() = runTest {
        val networkFailure = NetworkFailure.NoNetworkConnection(null)

        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackagePool(Arrangement.KEY_PACKAGE_LIMIT)
            .withGetAvailableKeyPackagesFailing(networkFailure)
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(actual.failure, networkFailure)
    }

    @Test
    fun givenThousandKeyPackagesMissing_whenRefilling_thenUploadsTenBatchesEachInItsOwnTransaction() = runTest {
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackagePool(Arrangement.MIGRATION_POOL_SIZE)
            .withKeyPackageCount(0)
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Success>(actual)
        verifySuspend(VerifyMode.exactly(10)) {
            arrangement.keyPackageRepository.uploadNewKeyPackages(any(), TestClient.CLIENT_ID, Arrangement.BATCH_SIZE)
        }
        // One transaction to read the cipher suite, then one per batch.
        verifySuspend(VerifyMode.exactly(11)) {
            arrangement.cryptoTransactionProvider.mlsTransaction<Any?>(any(), any())
        }
        verifySuspend(VerifyMode.exactly(10)) {
            arrangement.cryptoStateChangeHookNotifier.onCryptoStateChanged(TestUser.SELF.id)
        }
    }

    @Test
    fun givenMissingAmountIsNotAMultipleOfTheBatchSize_whenRefilling_thenTheLastBatchUploadsTheRest() = runTest {
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackagePool(Arrangement.MIGRATION_POOL_SIZE)
            .withKeyPackageCount(499)
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        refillKeyPackagesUseCase()

        verifySuspend(VerifyMode.exactly(5)) {
            arrangement.keyPackageRepository.uploadNewKeyPackages(any(), TestClient.CLIENT_ID, Arrangement.BATCH_SIZE)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageRepository.uploadNewKeyPackages(any(), TestClient.CLIENT_ID, 1)
        }
    }

    @Test
    fun givenBatchUploadFails_whenRefilling_thenStopsAndReturnsTheFailure() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackagePool(Arrangement.MIGRATION_POOL_SIZE)
            .withKeyPackageCount(1)
            .withUploadKeyPackagesFailing(failure)
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(failure, actual.failure)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageRepository.uploadNewKeyPackages(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoStateChangeHookNotifier.onCryptoStateChanged(any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val keyPackageRepository: KeyPackageRepository = mock()
        val keyPackageLimitsProvider: KeyPackageLimitsProvider = mock()
        val currentClientIdProvider: CurrentClientIdProvider = mock()
        val cryptoStateChangeHookNotifier = mock<CryptoStateChangeHookNotifier>(mode = MockMode.autoUnit)

        private var refillKeyPackageUseCase = RefillKeyPackagesUseCaseImpl(
            keyPackageRepository,
            keyPackageLimitsProvider,
            currentClientIdProvider,
            TestUser.SELF.id,
            cryptoStateChangeHookNotifier,
            cryptoTransactionProvider,
        )

        fun withDefaultCipherSuite(cipherSuite: CipherSuite) = apply {
            every {
                mlsContext.getDefaultCipherSuite()
            } returns cipherSuite.toCrypto()
        }

        suspend fun withExistingSelfClientId() = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(TestClient.CLIENT_ID)
        }

        suspend fun withKeyPackagePool(targetSize: Int, batchSize: Int = BATCH_SIZE) = apply {
            everySuspend {
                keyPackageLimitsProvider.keyPackagePool()
            } returns KeyPackagePool(targetSize)
            every {
                keyPackageLimitsProvider.refillAmount()
            } returns batchSize
        }

        suspend fun withKeyPackageCount(count: Int) = apply {
            everySuspend {
                keyPackageRepository.getAvailableKeyPackageCount(any(), any())
            } returns Either.Right(KeyPackageCountDTO(count))
        }

        suspend fun withUploadKeyPackagesFailing(failure: CoreFailure) = apply {
            everySuspend {
                keyPackageRepository.uploadNewKeyPackages(any(), TestClient.CLIENT_ID, any())
            } returns Either.Left(failure)
        }

        suspend fun withUploadKeyPackagesSuccessful() = apply {
            everySuspend {
                keyPackageRepository.uploadNewKeyPackages(any(), eq(TestClient.CLIENT_ID), any())
            } returns Either.Right(Unit)
        }

        suspend fun withGetAvailableKeyPackagesFailing(failure: NetworkFailure) = apply {
            everySuspend {
                keyPackageRepository.getAvailableKeyPackageCount(any(), any())
            } returns Either.Left(failure)
        }

        suspend fun arrange() = apply {
            withMLSTransactionReturning(Either.Right(Unit))
        }.let {
            this to refillKeyPackageUseCase
        }

        companion object {
            const val KEY_PACKAGE_LIMIT = 100
            const val KEY_PACKAGE_THRESHOLD = 0.5F
            const val BATCH_SIZE = 100
            const val MIGRATION_POOL_SIZE = 1000
        }

    }
}
