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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.LoginDomainPath
import com.wire.kalium.logic.data.auth.login.DomainLookupResult
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
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
        assertEquals(EnterpriseLoginResult.Success(LoginRedirectPath.Default), result)
    }

    @Test
    fun givenEmail_whenInvokedAndError_thenBubbleUpError() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(NetworkFailure.ServerMiscommunication(RuntimeException()).left())
            .arrange()

        val result = useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
        assertEquals(EnterpriseLoginResult.Failure.Generic::class, result::class)
    }

    @Test
    fun givenEmail_whenInvokedAndErrorBecauseOfNotSupported_thenReturnNotSupported() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(NetworkFailure.FeatureNotSupported.left())
            .arrange()

        val result = useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
        assertEquals(EnterpriseLoginResult.Failure.NotSupported, result)
    }

    @Test
    fun givenEmail_whenInvokedCustomBackend_thenFetchTheConfigurationAndMapIt() = runTest {
        val backendConfigUrl = "https://custom-backend.com/domain_redirect_backend.json"
        val configJsonUrl = "https://custom-backend.com/backend_config.json"
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(LoginDomainPath.CustomBackend(backendConfigUrl).right())
            .withDomainRedirectCustomBackendConfig(DomainLookupResult(configJsonUrl, "").right())
            .withServerLinksResult(ServerConfig.DUMMY.right())
            .arrange()

        val result = useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
        coVerify { arrangement.loginRepository.fetchDomainRedirectCustomBackendConfig(backendConfigUrl) }
        coVerify { arrangement.customServerConfigRepository.fetchRemoteConfig(configJsonUrl) }
        assertEquals(
            EnterpriseLoginResult.Success(LoginRedirectPath.CustomBackend(ServerConfig.DUMMY)),
            result,
        )
    }

    @Test
    fun givenEmail_whenInvokedAndErrorBecauseOfEnterpriseServiceNotEnabled_thenReturnSuccessNoRegistration() = runTest {
        val enterpriseServiceNotEnabledError = KaliumException.ServerError(ErrorResponse(503, "", "enterprise-service-not-enabled"))
        val (arrangement, useCase) = Arrangement()
            .withDomainRegistrationResult(NetworkFailure.ServerMiscommunication(enterpriseServiceNotEnabledError).left())
            .arrange()

        val result = useCase(Arrangement.EMAIL)

        coVerify { arrangement.loginRepository.getDomainRegistration(eq(Arrangement.EMAIL)) }
        assertEquals(EnterpriseLoginResult.Success(LoginRedirectPath.Default), result)
    }

    private class Arrangement {

        @Mock
        val loginRepository: LoginRepository = mock(LoginRepository::class)

        @Mock
        val customServerConfigRepository = mock(CustomServerConfigRepository::class)

        suspend fun withDomainRegistrationResult(result: Either<NetworkFailure, LoginDomainPath>) = apply {
            coEvery { loginRepository.getDomainRegistration(any()) }.returns(result)
        }

        suspend fun withDomainRedirectCustomBackendConfig(result: Either<NetworkFailure, DomainLookupResult>) = apply {
            coEvery { loginRepository.fetchDomainRedirectCustomBackendConfig(any()) }.returns(result)
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
