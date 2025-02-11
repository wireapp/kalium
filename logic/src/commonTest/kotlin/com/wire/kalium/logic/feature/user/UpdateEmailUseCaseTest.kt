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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertIs

class UpdateEmailUseCaseTest {

    @Test
    fun givenUpdateEmailSuccess_whenInvoked_thenReturnsVerificationEmailSent() = runTest {
        val (arrange, useCase) = Arrangement()
            .withUpdateSelfEmailSuccess(true)
            .arrange()
        val result = useCase("email")
        assertIs<UpdateEmailUseCase.Result.Success.VerificationEmailSent>(result)

        coVerify {
            arrange.accountRepository.updateSelfEmail(eq("email"))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdateEmailSuccess_whenInvoked_thenReturnsSuccess() = runTest {
        val (arrange, useCase) = Arrangement()
            .withUpdateSelfEmailSuccess(false)
            .arrange()
        val result = useCase("email")
        assertIs<UpdateEmailUseCase.Result.Success.NoChange>(result)

        coVerify {
            arrange.accountRepository.updateSelfEmail(eq("email"))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdateEmailFailure_whenInvoked_thenReturnsFailure() = runTest {
        val (arrange, useCase) = Arrangement()
            .withUpdateSelfEmailFailure(NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException())))
            .arrange()
        val result = useCase("email")
        assertIs<UpdateEmailUseCase.Result.Failure.GenericFailure>(result)

        coVerify {
            arrange.accountRepository.updateSelfEmail(eq("email"))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEmailAlreadyInUse_whenUpdatingEmail_thenEmailAlreadyInUseErrorIsReturned() = runTest {
        val (arrange, useCase) = Arrangement()
            .withUpdateSelfEmailFailure(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            400,
                            "message",
                            "key-exists"
                        )
                    )
                )
            )
            .arrange()
        val result = useCase("email")
        assertIs<UpdateEmailUseCase.Result.Failure.EmailAlreadyInUse>(result)

        coVerify {
            arrange.accountRepository.updateSelfEmail(eq("email"))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenInvalidEmail_whenUpdatingEmail_thenInvalidEmailErrorIsReturned() = runTest {
        val (arrange, useCase) = Arrangement()
            .withUpdateSelfEmailFailure(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            400,
                            "message",
                            "invalid-email"
                        )
                    )
                )
            )
            .arrange()
        val result = useCase("email")
        assertIs<UpdateEmailUseCase.Result.Failure.InvalidEmail>(result)

        coVerify {
            arrange.accountRepository.updateSelfEmail(eq("email"))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val accountRepository = mock(AccountRepository::class)

        private val useCase = UpdateEmailUseCase(accountRepository)

        suspend fun withUpdateSelfEmailSuccess(isEmailUpdated: Boolean) = apply {
            coEvery {
                accountRepository.updateSelfEmail(any())
            }.returns(Either.Right(isEmailUpdated))
        }

        suspend fun withUpdateSelfEmailFailure(error: NetworkFailure) = apply {
            coEvery {
                accountRepository.updateSelfEmail(any())
            }.returns(Either.Left(error))
        }

        fun arrange() = this to useCase
    }
}
