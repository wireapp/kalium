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
package com.wire.kalium.logic.feature.auth.verification

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.auth.verification.VerifiableAction
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RequestSecondFactorVerificationCodeUseCaseTest {

    @Test
    fun givenRepositorySucceeds_whenRequesting2FACode_thenShouldPropagateSuccess() = runTest {
        val (_, requestSecondFactorVerificationCodeUseCase) = Arrangement()
            .withRepositoryReturning(Either.Right(Unit))
            .arrange()

        val result = requestSecondFactorVerificationCodeUseCase(
            email = "email",
            verifiableAction = VerifiableAction.LOGIN_OR_CLIENT_REGISTRATION
        )

        assertIs<RequestSecondFactorVerificationCodeUseCase.Result.Success>(result)
    }

    @Test
    fun givenRepositoryFails_whenRequesting2FACode_thenShouldPropagateFailure() = runTest {
        val networkFailure = NetworkFailure.NoNetworkConnection(null)
        val (_, requestSecondFactorVerificationCodeUseCase) = Arrangement()
            .withRepositoryReturning(Either.Left(networkFailure))
            .arrange()

        val result = requestSecondFactorVerificationCodeUseCase(
            email = "email",
            verifiableAction = VerifiableAction.LOGIN_OR_CLIENT_REGISTRATION
        )

        assertIs<RequestSecondFactorVerificationCodeUseCase.Result.Failure.Generic>(result)
        assertEquals(networkFailure, result.cause)
    }

    @Test
    fun givenRepositoryFailsWithTooManyRequests_whenRequesting2FACode_thenShouldFailWithTooManyRequests() = runTest {
        val networkFailure = NetworkFailure.ServerMiscommunication(
            KaliumException.InvalidRequestError(
                ErrorResponse(HttpStatusCode.TooManyRequests.value, "", "too-many-requests")
            )
        )
        val (_, requestSecondFactorVerificationCodeUseCase) = Arrangement()
            .withRepositoryReturning(Either.Left(networkFailure))
            .arrange()

        val result = requestSecondFactorVerificationCodeUseCase(
            email = "email",
            verifiableAction = VerifiableAction.LOGIN_OR_CLIENT_REGISTRATION
        )

        assertIs<RequestSecondFactorVerificationCodeUseCase.Result.Failure.TooManyRequests>(result)
    }

    private class Arrangement {

        @Mock
        val secondFactorVerificationRepository = mock(SecondFactorVerificationRepository::class)

        suspend fun withRepositoryReturning(result: Either<NetworkFailure, Unit>) = apply {
            coEvery {
                secondFactorVerificationRepository.requestVerificationCode(any(), any())
            }.returns(result)
        }

        fun arrange() = this to RequestSecondFactorVerificationCodeUseCase(
            secondFactorVerificationRepository = secondFactorVerificationRepository
        )

    }
}
