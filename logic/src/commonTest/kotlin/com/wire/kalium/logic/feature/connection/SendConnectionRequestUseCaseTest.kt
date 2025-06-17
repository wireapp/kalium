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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SendConnectionRequestUseCaseTest {

    @Test
    fun givenAConnectionRequest_whenInvokingASendAConnectionRequest_thenShouldReturnsASuccessResult() = runTest {
        // given
        val (arrangement, sendConnectionRequestUseCase) = Arrangement()
            .withCreateConnectionResult(Either.Right(Unit))
            .withFetchUserInfoResult(Either.Right(Unit))
            .arrange()

        // when
        val resultOk = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Success, resultOk)
        coVerify {
            arrangement.userRepository.fetchUserInfo(eq(userId))
        }.wasInvoked(once)
        coVerify {
            arrangement.connectionRepository.sendUserConnection(eq(userId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingFetchUserInfoRequestFails_thenShouldReturnsAFailureResult() = runTest {
        // given
        val (arrangement, sendConnectionRequestUseCase) = Arrangement()
            .withFetchUserInfoResult(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))
            .withCreateConnectionResult(Either.Right(Unit))
            .arrange()

        // when
        val resultFailure = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Failure.GenericFailure::class, resultFailure::class)
        coVerify {
            arrangement.userRepository.fetchUserInfo(eq(userId))
        }.wasInvoked(once)
        coVerify {
            arrangement.connectionRepository.sendUserConnection(eq(userId))
        }.wasNotInvoked()
    }

    @Test
    fun givenAConnectionRequest_whenInvokingASendAConnectionRequestFails_thenShouldReturnsAFailureResult() = runTest {
        // given
        val (arrangement, sendConnectionRequestUseCase) = Arrangement()
            .withFetchUserInfoResult(Either.Right(Unit))
            .withCreateConnectionResult(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))
            .arrange()

        // when
        val resultFailure = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Failure.GenericFailure::class, resultFailure::class)
        coVerify {
            arrangement.connectionRepository.sendUserConnection(eq(userId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingAndFailsByFederationDenied_thenShouldReturnsAFederationDenied() = runTest {
        // given
        val (arrangement, sendConnectionRequestUseCase) = Arrangement()
            .withFetchUserInfoResult(Either.Right(Unit))
            .withCreateConnectionResult(
                Either.Left(NetworkFailure.FederatedBackendFailure.FederationDenied("federation-denied"))
            )
            .arrange()
        // when
        val resultFailure = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Failure.FederationDenied::class, resultFailure::class)
        coVerify {
            arrangement.connectionRepository.sendUserConnection(eq(userId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingAndFailsByMissingLegalHoldConsent_thenShouldReturnsAMissingLegalHoldConsent() = runTest {
        // given
        val (arrangement, sendConnectionRequestUseCase) = Arrangement()
            .withFetchUserInfoResult(Either.Right(Unit))
            .withCreateConnectionResult(
                Either.Left(
                    NetworkFailure.ServerMiscommunication(
                        KaliumException.InvalidRequestError(ErrorResponse(403, "", "missing-legalhold-consent"))
                    )
                )
            )
            .arrange()
        // when
        val resultFailure = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Failure.MissingLegalHoldConsent::class, resultFailure::class)
        coVerify {
            arrangement.connectionRepository.sendUserConnection(eq(userId))
        }.wasInvoked(once)
    }

    private class Arrangement {
                val connectionRepository = mock(ConnectionRepository::class)
        val userRepository = mock(UserRepository::class)

        suspend fun withCreateConnectionResult(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                connectionRepository.sendUserConnection(eq(userId))
            }.returns(result)
        }

        suspend fun withFetchUserInfoResult(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                userRepository.fetchUserInfo(any())
            }.returns(result)
        }

        fun arrange() = this to SendConnectionRequestUseCaseImpl(connectionRepository, userRepository)
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }
}
