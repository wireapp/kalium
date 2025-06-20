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
package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.unauthenticated.sso.SSOSettingsResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FetchSSOSettingsUseCaseTest {

    @Test
    fun givenSuccess_whenInvoked_thenReturnsSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSSOSettings(Either.Right(SSOSettingsResponse("c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2")))
            .arrange()

        useCase().also { result ->
            assertIs<FetchSSOSettingsUseCase.Result.Success>(result)
            assertEquals("c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2", result.defaultSSOCode)
        }

        coVerify {
            arrangement.ssoLoginRepository.settings()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenInvoked_thenReturnsError() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSSOSettings(Either.Left(NetworkFailure.NoNetworkConnection(IOException())))
            .arrange()

        useCase().also { result ->
            assertIs<FetchSSOSettingsUseCase.Result.Failure>(result)
        }

        coVerify {
            arrangement.ssoLoginRepository.settings()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun given404Error_whenInvoked_thenReturnSuccessWithNoCode() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSSOSettings(
                Either.Left(
                    NetworkFailure.ServerMiscommunication(
                        KaliumException.InvalidRequestError(
                            ErrorResponse(404, "Not Found", "Not Found")
                        )
                    )
                )
            )
            .arrange()

        useCase().also { result ->
            assertIs<FetchSSOSettingsUseCase.Result.Success>(result)
            assertNull(result.defaultSSOCode)
        }

        coVerify {
            arrangement.ssoLoginRepository.settings()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        val ssoLoginRepository: SSOLoginRepository = mock(SSOLoginRepository::class)

        private val useCase: FetchSSOSettingsUseCase = FetchSSOSettingsUseCase(ssoLoginRepository)

        suspend fun withSSOSettings(result: Either<NetworkFailure, SSOSettingsResponse>) = apply {
            coEvery {
                ssoLoginRepository.settings()
            }.returns(result)
        }
        fun arrange() = this to useCase
    }
}
