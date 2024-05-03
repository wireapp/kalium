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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.CLIENT_FETCH_ERROR
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.KEY_PACKAGE_COUNT
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.KEY_PACKAGE_COUNT_DTO
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseTest.Arrangement.Companion.NETWORK_FAILURE
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageCountDTO
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MLSKeyPackageCountUseCaseTest {

    @Test
    fun givenClientIdIsNotRegistered_ThenReturnGenericError() = runTest {
        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withClientId(Either.Left(CLIENT_FETCH_ERROR))
            .arrange()

        val actual = keyPackageCountUseCase()

        coVerify {
            arrangement.keyPackageRepository.getAvailableKeyPackageCount(eq(TestClient.CLIENT_ID))
        }.wasNotInvoked()

        assertIs<MLSKeyPackageCountResult.Failure.FetchClientIdFailure>(actual)
        assertEquals(actual.genericFailure, CLIENT_FETCH_ERROR)
    }

    @Test
    fun givenClientId_whenCallingKeyPackageCountReturnValue_ThenReturnKeyPackageCountSuccess() = runTest {
        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withAvailableKeyPackageCountReturn(Either.Right(KEY_PACKAGE_COUNT_DTO))
            .withClientId(Either.Right(TestClient.CLIENT_ID))
            .withKeyPackageLimitSucceed()
            .arrange()

        val actual = keyPackageCountUseCase()

        coVerify {
            arrangement.keyPackageRepository.getAvailableKeyPackageCount(eq(TestClient.CLIENT_ID))
        }.wasInvoked(once)
        assertIs<MLSKeyPackageCountResult.Success>(actual)
        assertEquals(actual, MLSKeyPackageCountResult.Success(TestClient.CLIENT_ID, KEY_PACKAGE_COUNT, true))
    }

    @Test
    fun givenClientID_whenCallingKeyPackageCountReturnError_ThenReturnKeyPackageCountFailure() = runTest {
        val (arrangement, keyPackageCountUseCase) = Arrangement()
            .withAvailableKeyPackageCountReturn(Either.Left(NETWORK_FAILURE))
            .withClientId(Either.Right(TestClient.CLIENT_ID))
            .arrange()

        val actual = keyPackageCountUseCase()

        coVerify {
            arrangement.keyPackageRepository.getAvailableKeyPackageCount(eq(TestClient.CLIENT_ID))
        }.wasInvoked(once)
        assertIs<MLSKeyPackageCountResult.Failure.NetworkCallFailure>(actual)
        assertEquals(actual.networkFailure, NETWORK_FAILURE)
    }

    private class Arrangement {
        @Mock
        val keyPackageRepository = mock(KeyPackageRepository::class)

        @Mock
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val keyPackageLimitsProvider = mock(KeyPackageLimitsProvider::class)

        suspend fun withClientId(result: Either<CoreFailure, ClientId>) = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(result)
        }

        fun withKeyPackageLimitSucceed() = apply {
            every {
                keyPackageLimitsProvider.needsRefill(any())
            }.returns(true)
        }

        suspend fun withAvailableKeyPackageCountReturn(result: Either<NetworkFailure, KeyPackageCountDTO>) = apply {
            coEvery {
                keyPackageRepository.getAvailableKeyPackageCount(any())
            }.returns(result)
        }

        fun arrange() = this to MLSKeyPackageCountUseCaseImpl(
            keyPackageRepository, currentClientIdProvider, keyPackageLimitsProvider
        )

        companion object {
            val NETWORK_FAILURE = NetworkFailure.NoNetworkConnection(null)

            val CLIENT_FETCH_ERROR = CoreFailure.MissingClientRegistration
            const val KEY_PACKAGE_COUNT = 10
            val KEY_PACKAGE_COUNT_DTO = KeyPackageCountDTO(KEY_PACKAGE_COUNT)
        }
    }
}
