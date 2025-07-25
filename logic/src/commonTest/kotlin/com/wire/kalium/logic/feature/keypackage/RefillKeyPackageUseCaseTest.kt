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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
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

        val actual = refillKeyPackagesUseCase(arrangement.mlsContext)

        coVerify {
            arrangement.keyPackageRepository.uploadNewKeyPackages(
                any(),
                eq(TestClient.CLIENT_ID),
                eq(Arrangement.KEY_PACKAGE_LIMIT - keyPackageCount)
            )
        }.wasInvoked(once)

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

        val actual = refillKeyPackagesUseCase(arrangement.mlsContext)

        assertIs<RefillKeyPackagesResult.Success>(actual)
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

        val actual = refillKeyPackagesUseCase(arrangement.mlsContext)

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(actual.failure, networkFailure)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val keyPackageRepository = mock(KeyPackageRepository::class)
        val keyPackageLimitsProvider = mock(KeyPackageLimitsProvider::class)
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        private var refillKeyPackageUseCase = RefillKeyPackagesUseCaseImpl(
            keyPackageRepository,
            keyPackageLimitsProvider,
            currentClientIdProvider,
        )

        fun withDefaultCipherSuite(cipherSuite: CipherSuite) = apply {
            every {
                mlsContext.getDefaultCipherSuite()
            }.returns(cipherSuite.toCrypto())
        }

        suspend fun withExistingSelfClientId() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(TestClient.CLIENT_ID))
        }

        fun withKeyPackageLimits(needRefill: Boolean, refillAmount: Int) = apply {
            every {
                keyPackageLimitsProvider.needsRefill(any())
            }.returns(needRefill)
            every {
                keyPackageLimitsProvider.refillAmount()
            }.returns(refillAmount)
        }

        suspend fun withKeyPackageCount(
            count: Int,
            clientId: AnyMatcher<ClientId> = AnyMatcher(valueOf()),
            cipherSuite: AnyMatcher<CipherSuite> = AnyMatcher(valueOf()),
        ) = apply {
            coEvery {
                keyPackageRepository.getAvailableKeyPackageCount(matches { clientId.matches(it) }, matches { cipherSuite.matches(it) })
            }.returns(Either.Right(KeyPackageCountDTO(count)))
        }

        suspend fun withUploadKeyPackagesSuccessful() = apply {
            coEvery {
                keyPackageRepository.uploadNewKeyPackages(any(), eq(TestClient.CLIENT_ID), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetAvailableKeyPackagesFailing(
            failure: NetworkFailure,
            clientId: AnyMatcher<ClientId> = AnyMatcher(valueOf()),
            cipherSuite: AnyMatcher<CipherSuite> = AnyMatcher(valueOf()),
        ) = apply {
            coEvery {
                keyPackageRepository.getAvailableKeyPackageCount(matches { clientId.matches(it) }, matches { cipherSuite.matches(it) })
            }.returns(Either.Left(failure))
        }

        suspend fun arrange() = apply {
            withMLSTransactionReturning(Either.Right(Unit))
        }.let {
            this to refillKeyPackageUseCase
        }

        companion object {
            const val KEY_PACKAGE_LIMIT = 100
            const val KEY_PACKAGE_THRESHOLD = 0.5F
        }

    }
}
