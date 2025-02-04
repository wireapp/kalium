/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.LoginDomainPath
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class GetLoginFlowForDomainUseCaseTest {

    @Test
    fun givenEmail_whenInvoked_thenReturnTheDomainPath() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(LoginDomainPath.None.right())
            .arrange()

        useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
    }

    @Test
    fun givenEmail_whenInvokedAndError_thenBubbleUpError() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(NetworkFailure.ServerMiscommunication(RuntimeException()).left())
            .arrange()

        useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
    }

    private class Arrangement {

        @Mock
        val loginRepository: LoginRepository = mock(LoginRepository::class)

        suspend fun withDomainRegistrationResult(result: Either<NetworkFailure, LoginDomainPath>) = apply {
            coEvery { loginRepository.getDomainRegistration(any()) }.returns(result)
        }

        fun arrange() = this to GetLoginFlowForDomainUseCase(loginRepository)

        companion object {
            const val EMAIL = "user@wire.com"
        }
    }
}
