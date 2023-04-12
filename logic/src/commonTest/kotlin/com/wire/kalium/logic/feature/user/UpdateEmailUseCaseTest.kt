/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateEmailUseCaseTest {

    @Test
    fun givenUpdateEmailSuccess_whenInvoked_thenReturnsSuccess() = runTest {
        val (arrange, useCase) = Arrangement()
            .withUpdateSelfEmailSuccess()
            .arrange()
        val result = useCase("email")
        assertIs<UpdateEmailUseCase.Result.Success>(result)

        verify(arrange.userRepository)
            .suspendFunction(arrange.userRepository::updateSelfEmail)
            .with(eq("email"))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdateEmailFailure_whenInvoked_thenReturnsFailure() = runTest {
        val (arrange, useCase) = Arrangement()
            .withUpdateSelfEmailFailure(NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException())))
            .arrange()
        val result = useCase("email")
        assertIs<UpdateEmailUseCase.Result.Failure.GenericFailure>(result)

        verify(arrange.userRepository)
            .suspendFunction(arrange.userRepository::updateSelfEmail)
            .with(eq("email"))
            .wasInvoked(exactly = once)
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

        verify(arrange.userRepository)
            .suspendFunction(arrange.userRepository::updateSelfEmail)
            .with(eq("email"))
            .wasInvoked(exactly = once)
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

        verify(arrange.userRepository)
            .suspendFunction(arrange.userRepository::updateSelfEmail)
            .with(eq("email"))
            .wasInvoked(exactly = once)
    }


    private class Arrangement {
        @Mock
        val userRepository = mock(UserRepository::class)

        private val useCase = UpdateEmailUseCase(userRepository)

        fun withUpdateSelfEmailSuccess() = apply {
            given(userRepository)
                .suspendFunction(userRepository::updateSelfEmail)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withUpdateSelfEmailFailure(error: NetworkFailure) = apply {
            given(userRepository)
                .suspendFunction(userRepository::updateSelfEmail)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(error))
        }

        fun arrange() = this to useCase
    }
}
