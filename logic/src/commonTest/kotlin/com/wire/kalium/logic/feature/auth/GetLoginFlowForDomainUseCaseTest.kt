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
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
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
import kotlin.test.assertEquals

class GetLoginFlowForDomainUseCaseTest {

    @Test
    fun givenEmail_whenInvoked_thenReturnTheDomainPath() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(LoginDomainPath.Default.right())
            .arrange()

        val result = useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
        assertEquals(result, EnterpriseLoginResult.Success(LoginRedirectPath.Default))
    }

    @Test
    fun givenEmail_whenInvokedAndError_thenBubbleUpError() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(NetworkFailure.ServerMiscommunication(RuntimeException()).left())
            .arrange()

        val result = useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
        assertEquals(result::class, EnterpriseLoginResult.Failure.Generic::class)
    }

    @Test
    fun givenEmail_whenInvokedAndError_thenReturnNoNetwork() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(NetworkFailure.NoNetworkConnection(RuntimeException()).left())
            .arrange()

        val result = useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
        assertEquals(result, EnterpriseLoginResult.Failure.NoNetwork)
    }

    @Test
    fun givenEmail_whenInvokedAndErrorBecauseOfNotSupported_thenReturnNotSupported() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(NetworkFailure.FeatureNotSupported.left())
            .arrange()

        val result = useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
        assertEquals(result, EnterpriseLoginResult.Failure.NotSupported)
    }

    private class Arrangement {

        @Mock
        val loginRepository: LoginRepository = mock(LoginRepository::class)

        @Mock
        val customServerConfigRepository = mock(CustomServerConfigRepository::class)

        suspend fun withDomainRegistrationResult(result: Either<NetworkFailure, LoginDomainPath>) = apply {
            coEvery { loginRepository.getDomainRegistration(any()) }.returns(result)
        }

        suspend fun withServerLinksResult(result: Either<NetworkFailure, ServerConfig.Links>) = apply {
            coEvery { customServerConfigRepository.fetchRemoteConfig(any()) }.returns(result)
        }

        fun arrange() = this to GetLoginFlowForDomainUseCase(loginRepository, customServerConfigRepository)

        companion object {
            const val EMAIL = "user@wire.com"
        }
    }
}
