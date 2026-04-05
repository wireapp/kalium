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
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.CLIENT_FETCH_ERROR
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.KEY_PACKAGE_COUNT
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.KEY_PACKAGE_COUNT_DTO
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.NETWORK_FAILURE
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MLSKeyPackageCountUseCaseTest {

    @Test
    fun givenClientIdIsNotRegistered_ThenReturnGenericError() = runTest {
        val expectedCipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withClientId(Either.Left(CLIENT_FETCH_ERROR))
            .withDefaultCipherSuite(expectedCipherSuite)
            .arrange {
                withGetMLSEnabledReturning(true.right())
            }

        val actual = keyPackageCountUseCase()

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.keyPackageRepository.getAvailableKeyPackageCount(TestClient.CLIENT_ID, expectedCipherSuite)
        }

        assertIs<MLSKeyPackageCountResult.Failure.FetchClientIdFailure>(actual)
        assertEquals(actual.genericFailure, CLIENT_FETCH_ERROR)
    }

    @Test
    fun givenClientId_whenCallingKeyPackageCountReturnValue_ThenReturnKeyPackageCountSuccess() = runTest {
        val expectedCipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withAvailableKeyPackageCountReturn(Either.Right(KEY_PACKAGE_COUNT_DTO))
            .withClientId(Either.Right(TestClient.CLIENT_ID))
            .withDefaultCipherSuite(expectedCipherSuite)
            .withKeyPackageLimitSucceed()
            .arrange {
                withGetMLSEnabledReturning(true.right())
            }

        val actual = keyPackageCountUseCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageRepository.getAvailableKeyPackageCount(TestClient.CLIENT_ID, expectedCipherSuite)
        }
        assertIs<MLSKeyPackageCountResult.Success>(actual)
        assertEquals(actual, MLSKeyPackageCountResult.Success(TestClient.CLIENT_ID, KEY_PACKAGE_COUNT, true))
    }

    @Test
    fun givenClientID_whenCallingKeyPackageCountReturnError_ThenReturnKeyPackageCountFailure() = runTest {
        val expectedCipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withAvailableKeyPackageCountReturn(Either.Left(NETWORK_FAILURE))
            .withClientId(Either.Right(TestClient.CLIENT_ID))
            .withDefaultCipherSuite(expectedCipherSuite)
            .arrange {
                withGetMLSEnabledReturning(true.right())
            }

        val actual = keyPackageCountUseCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageRepository.getAvailableKeyPackageCount(TestClient.CLIENT_ID, expectedCipherSuite)
        }
        assertIs<MLSKeyPackageCountResult.Failure.NetworkCallFailure>(actual)
        assertEquals(actual.networkFailure, NETWORK_FAILURE)
    }

    @Test
    fun givenClientID_whenCallingGetMLSEnabledReturnFalse_ThenReturnKeyPackageCountNotEnabledFailure() = runTest {
        val expectedCipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withAvailableKeyPackageCountReturn(Either.Right(KEY_PACKAGE_COUNT_DTO))
            .withClientId(Either.Right(TestClient.CLIENT_ID))
            .withDefaultCipherSuite(expectedCipherSuite)
            .arrange {
                withGetMLSEnabledReturning(false.right())
            }

        val actual = keyPackageCountUseCase()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.isMLSEnabled()
        }

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.keyPackageRepository.getAvailableKeyPackageCount(TestClient.CLIENT_ID, expectedCipherSuite)
        }
        assertIs<MLSKeyPackageCountResult.Failure.NotEnabled>(actual)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val keyPackageRepository = mock<KeyPackageRepository>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val keyPackageLimitsProvider = mock<KeyPackageLimitsProvider>()
        val userConfigRepository = mock<UserConfigRepository>()

        suspend fun withClientId(result: Either<CoreFailure, ClientId>) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns result
        }

        fun withKeyPackageLimitSucceed() = apply {
            dev.mokkery.every {
                keyPackageLimitsProvider.needsRefill(mokkeryAny())
            } returns true
        }

        suspend fun withAvailableKeyPackageCountReturn(result: Either<NetworkFailure, KeyPackageCountDTO>) = apply {
            everySuspend {
                keyPackageRepository.getAvailableKeyPackageCount(mokkeryAny(), mokkeryAny())
            } returns result
        }

        fun withDefaultCipherSuite(cipherSuite: CipherSuite) = apply {
            dev.mokkery.every {
                mlsContext.getDefaultCipherSuite()
            } returns cipherSuite.toCrypto()
        }

        suspend fun withGetMLSEnabledReturning(result: Either<StorageFailure, Boolean>) = apply {
            everySuspend {
                userConfigRepository.isMLSEnabled()
            } returns result
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit) = apply {
            withMLSTransactionReturning(Either.Right(Unit))
        }.apply {
            runBlocking { block() }
        }.let {
            this to MLSKeyPackageCountUseCaseImpl(
                keyPackageRepository = keyPackageRepository,
                currentClientIdProvider = currentClientIdProvider,
                keyPackageLimitsProvider = keyPackageLimitsProvider,
                userConfigRepository = userConfigRepository,
                transactionProvider = cryptoTransactionProvider
            )
        }

        companion object {
            val NETWORK_FAILURE = NetworkFailure.NoNetworkConnection(null)

            val CLIENT_FETCH_ERROR = CoreFailure.MissingClientRegistration
            const val KEY_PACKAGE_COUNT = 10
            val KEY_PACKAGE_COUNT_DTO = KeyPackageCountDTO(KEY_PACKAGE_COUNT)
        }
    }
}
