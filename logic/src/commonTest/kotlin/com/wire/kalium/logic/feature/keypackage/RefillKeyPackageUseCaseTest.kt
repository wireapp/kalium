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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
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
            .withKeyPackageLimits(true, Arrangement.KEY_PACKAGE_LIMIT - keyPackageCount)
            .withKeyPackageCount(keyPackageCount)
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.generateKeyPackages(Arrangement.KEY_PACKAGE_LIMIT - keyPackageCount, any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageRepository.uploadKeyPackages(TestClient.CLIENT_ID, Arrangement.KEY_PACKAGES)
        }

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenRefillKeyPackageUseCase_WhenNeedRefillReturnFalse_ThenRequestToRefillKeyPackageIsPerformed() = runTest {
        val keyPackageCount = (Arrangement.KEY_PACKAGE_LIMIT * Arrangement.KEY_PACKAGE_THRESHOLD).toInt()

        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(false, 0)
            .withKeyPackageCount(keyPackageCount)
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenNoKeyPackagesLeft_whenRefilling_thenPendingConversationsAreFlaggedBeforeUploading() = runTest {
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, Arrangement.KEY_PACKAGE_LIMIT)
            .withKeyPackageCount(0)
            .withSetShouldJoinPendingMLSConversationsResult(Either.Right(Unit))
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Success>(actual)
        verifySuspend(VerifyMode.order) {
            arrangement.userConfigRepository.setShouldJoinPendingMLSConversations(true)
            arrangement.keyPackageRepository.uploadKeyPackages(TestClient.CLIENT_ID, any())
        }
    }

    @Test
    fun givenNoKeyPackagesLeftAndFlaggingFails_whenRefilling_thenKeyPackagesAreNotUploaded() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, Arrangement.KEY_PACKAGE_LIMIT)
            .withKeyPackageCount(0)
            .withSetShouldJoinPendingMLSConversationsResult(Either.Left(failure))
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(failure, actual.failure)
        verifySuspend(VerifyMode.not) {
            arrangement.mlsContext.generateKeyPackages(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.keyPackageRepository.uploadKeyPackages(any(), any())
        }
    }

    @Test
    fun givenSomeKeyPackagesLeft_whenRefilling_thenPendingConversationsAreNotFlagged() = runTest {
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, Arrangement.KEY_PACKAGE_LIMIT - 1)
            .withKeyPackageCount(1)
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        refillKeyPackagesUseCase()

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setShouldJoinPendingMLSConversations(any())
        }
    }

    @Test
    fun givenErrorIsEncountered_ThenFailureIsPropagated() = runTest {
        val networkFailure = NetworkFailure.NoNetworkConnection(null)

        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, 0)
            .withGetAvailableKeyPackagesFailing(networkFailure)
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(actual.failure, networkFailure)
    }

    @Test
    fun givenThousandKeyPackagesMissing_whenRefilling_thenGeneratesTenBatchesEachInItsOwnTransaction() = runTest {
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, 1000)
            .withKeyPackageCount(1)
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Success>(actual)
        verifySuspend(VerifyMode.exactly(10)) {
            arrangement.mlsContext.generateKeyPackages(Arrangement.BATCH_SIZE, any())
        }
        verifySuspend(VerifyMode.exactly(10)) {
            arrangement.keyPackageRepository.uploadKeyPackages(TestClient.CLIENT_ID, Arrangement.KEY_PACKAGES)
        }
        // One transaction to read the cipher suite, then one per batch.
        verifySuspend(VerifyMode.exactly(11)) {
            arrangement.cryptoTransactionProvider.mlsTransaction<Any?>(any(), any())
        }
    }

    @Test
    fun givenBatches_whenRefilling_thenEachBatchIsUploadedOnlyAfterItsTransactionHasReturned() = runTest {
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, 1000)
            .withKeyPackageCount(1)
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        refillKeyPackagesUseCase()

        assertEquals(List(10) { false }, arrangement.uploadsInsideTransaction)
    }

    @Test
    fun givenMissingAmountIsNotAMultipleOfTheBatchSize_whenRefilling_thenTheLastBatchGeneratesTheRest() = runTest {
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, 501)
            .withKeyPackageCount(499)
            .withUploadKeyPackagesSuccessful()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        refillKeyPackagesUseCase()

        verifySuspend(VerifyMode.exactly(5)) {
            arrangement.mlsContext.generateKeyPackages(Arrangement.BATCH_SIZE, any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.generateKeyPackages(1, any())
        }
    }

    @Test
    fun givenBatchUploadFails_whenRefilling_thenStopsAndReturnsTheFailure() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, 1000)
            .withKeyPackageCount(1)
            .withUploadKeyPackagesFailing(failure)
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(failure, actual.failure)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.generateKeyPackages(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageRepository.uploadKeyPackages(any(), any())
        }
    }

    @Test
    fun givenGeneratingKeyPackagesFails_whenRefilling_thenNothingIsUploaded() = runTest {
        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, 1000)
            .withKeyPackageCount(1)
            .withGeneratingKeyPackagesFailing()
            .withDefaultCipherSuite(CipherSuite.fromTag(1))
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        verifySuspend(VerifyMode.not) {
            arrangement.keyPackageRepository.uploadKeyPackages(any(), any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val keyPackageRepository: KeyPackageRepository = mock()
        val keyPackageLimitsProvider: KeyPackageLimitsProvider = mock()
        val currentClientIdProvider: CurrentClientIdProvider = mock()
        val userConfigRepository: UserConfigRepository = mock(MockMode.autoUnit)

        /** Whether each upload happened while an MLS transaction was still open. */
        val uploadsInsideTransaction = mutableListOf<Boolean>()
        private var openTransactions = 0

        private var refillKeyPackageUseCase = RefillKeyPackagesUseCaseImpl(
            keyPackageRepository,
            keyPackageLimitsProvider,
            currentClientIdProvider,
            userConfigRepository,
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

        fun withKeyPackageLimits(needRefill: Boolean, refillAmount: Int, batchSize: Int = BATCH_SIZE) = apply {
            every {
                keyPackageLimitsProvider.needsRefill(any())
            } returns needRefill
            every {
                keyPackageLimitsProvider.missingKeyPackages(any())
            } returns refillAmount
            every {
                keyPackageLimitsProvider.refillAmount()
            } returns batchSize
        }

        suspend fun withKeyPackageCount(count: Int) = apply {
            everySuspend {
                keyPackageRepository.getAvailableKeyPackageCount(any(), any())
            } returns Either.Right(KeyPackageCountDTO(count))
        }

        suspend fun withGeneratingKeyPackagesFailing() = apply {
            everySuspend {
                mlsContext.generateKeyPackages(any(), any())
            } throws IllegalStateException("generation failed")
        }

        suspend fun withUploadKeyPackagesFailing(failure: CoreFailure) = apply {
            withUploadKeyPackagesReturning(Either.Left(failure))
        }

        suspend fun withUploadKeyPackagesSuccessful() = apply {
            withUploadKeyPackagesReturning(Either.Right(Unit))
        }

        private suspend fun withUploadKeyPackagesReturning(result: Either<CoreFailure, Unit>) {
            everySuspend {
                mlsContext.generateKeyPackages(any(), any())
            } returns KEY_PACKAGES
            everySuspend {
                keyPackageRepository.uploadKeyPackages(TestClient.CLIENT_ID, any())
            } calls {
                uploadsInsideTransaction += openTransactions > 0
                result
            }
        }

        suspend fun withSetShouldJoinPendingMLSConversationsResult(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                userConfigRepository.setShouldJoinPendingMLSConversations(any())
            } returns result
        }

        suspend fun withGetAvailableKeyPackagesFailing(failure: NetworkFailure) = apply {
            everySuspend {
                keyPackageRepository.getAvailableKeyPackageCount(any(), any())
            } returns Either.Left(failure)
        }

        @Suppress("UNCHECKED_CAST")
        suspend fun arrange(): Pair<Arrangement, RefillKeyPackagesUseCaseImpl> {
            everySuspend {
                cryptoTransactionProvider.mlsTransaction<Any?>(any(), any())
            } calls {
                val block = it.args[1] as suspend (MlsCoreCryptoContext) -> Either<CoreFailure, Any?>
                openTransactions++
                try {
                    block(mlsContext)
                } finally {
                    openTransactions--
                }
            }
            return this to refillKeyPackageUseCase
        }

        companion object {
            const val KEY_PACKAGE_LIMIT = 100
            const val KEY_PACKAGE_THRESHOLD = 0.5F
            const val BATCH_SIZE = 100
            val KEY_PACKAGES = listOf(byteArrayOf(1, 2, 3))
        }

    }
}
