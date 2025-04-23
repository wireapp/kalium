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

package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.network.api.base.unauthenticated.domainLookup.DomainLookupApi
import com.wire.kalium.network.api.unauthenticated.domainLookup.DomainLookupResponse
import com.wire.kalium.network.api.unauthenticated.sso.InitiateParam
import com.wire.kalium.network.api.base.unauthenticated.sso.SSOLoginApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SSOLoginRepositoryTest {

        val ssoLogin = mock(SSOLoginApi::class)

        val domainLookup = mock(DomainLookupApi::class)

    private lateinit var ssoLoginRepository: SSOLoginRepository

    @BeforeTest
    fun setup() {
        ssoLoginRepository = SSOLoginRepositoryImpl(ssoLogin, domainLookup)
    }

    @Test
    fun givenApiRequestSuccess_whenInitiatingWithoutRedirects_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            { initiate(InitiateParam.WithoutRedirect(TEST_CODE)) },
            "wire/response",
            { ssoLoginRepository.initiate(TEST_CODE) }
        )

    @Test
    fun givenApiRequestSuccess_whenInitiatingWithRedirects_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            { initiate(InitiateParam.WithRedirect(TEST_SUCCESS, TEST_ERROR, TEST_CODE)) },
            "wire/response",
            { ssoLoginRepository.initiate(TEST_CODE, TEST_SUCCESS, TEST_ERROR) }
        )

    @Test
    fun givenApiRequestFail_whenInitiating_thenNetworkFailureIsPropagated() =
        givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
            { initiate(InitiateParam.WithoutRedirect(TEST_CODE)) },
            expected = TestNetworkException.generic,
            { ssoLoginRepository.initiate(TEST_CODE) }
        )

    @Test
    fun givenApiRequestSuccess_whenFinalizing_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            apiCoroutineBlock = { finalize(TEST_COOKIE) },
            expected = "wire/response",
            repositoryCoroutineBlock = { finalize(TEST_COOKIE) }
        )

    @Test
    fun givenApiRequestFail_whenFinalizing_thenNetworkFailureIsPropagated() =
        givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
            apiCoroutineBlock = { finalize(TEST_COOKIE) },
            expected = TestNetworkException.generic,
            repositoryCoroutineBlock = { finalize(TEST_COOKIE) }
        )

    @Test
    fun givenApiRequestSuccess_whenRequestingMetaData_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            apiCoroutineBlock = { metaData() },
            expected = "wire/response",
            repositoryCoroutineBlock = { metaData() }
        )

    @Test
    fun givenApiRequestFail_whenRequestingMetaData_thenNetworkFailureIsPropagated() =
        givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
            apiCoroutineBlock = { metaData() },
            expected = TestNetworkException.generic,
            repositoryCoroutineBlock = { metaData() }
        )

    @Test
    fun givenApiRequestSuccess_whenRequestingSettings_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            apiCoroutineBlock = { settings() },
            expected = true,
            repositoryCoroutineBlock = { settings() }
        )

    @Test
    fun givenApiRequestFail_whenRequestingSettings_thenNetworkFailureIsPropagated() =
        givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
            apiCoroutineBlock = { settings() },
            expected = TestNetworkException.generic,
            repositoryCoroutineBlock = { settings() }
        )

    @Test
    fun givenDomainLookupSuccess_thenSuccesIsPropagated() = runTest {
        val domain = "test.com"
        val networkResponse = DomainLookupResponse(
            configJsonUrl = "https://test.com/config.json",
            webappWelcomeUrl = "https://test.com/welcome"
        )

        coEvery {
            domainLookup.lookup(domain)
        }.returns(NetworkResponse.Success(networkResponse, mapOf(), 200))
        val actual = ssoLoginRepository.domainLookup(domain)

        assertIs<Either.Right<DomainLookupResult>>(actual)
        assertEquals(
            DomainLookupResult(
                networkResponse.configJsonUrl,
                networkResponse.webappWelcomeUrl
            ),
            actual.value
        )

        coVerify {
            domainLookup.lookup(any())
        }.wasInvoked(exactly = once)
    }

    private fun <T : Any> givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
        apiCoroutineBlock: suspend SSOLoginApi.() -> NetworkResponse<T>,
        expected: T,
        repositoryCoroutineBlock: suspend SSOLoginRepository.() -> Either<NetworkFailure, T>
    ) = runTest {
        coEvery {
            ssoLogin.apiCoroutineBlock()
        }.returns(NetworkResponse.Success(expected, mapOf(), 200))
        val actual = repositoryCoroutineBlock(ssoLoginRepository)
        assertIs<Either.Right<T>>(actual)
        assertEquals(expected, actual.value)
        coVerify {
            ssoLogin.apiCoroutineBlock()
        }.wasInvoked(exactly = once)
    }

    private fun <T : Any> givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
        apiCoroutineBlock: suspend SSOLoginApi.() -> NetworkResponse<T>,
        expected: KaliumException,
        repositoryCoroutineBlock: suspend SSOLoginRepository.() -> Either<NetworkFailure, T>
    ) = runTest {
        coEvery {
            ssoLogin.apiCoroutineBlock()
        }.returns(NetworkResponse.Error(expected))
        val actual = repositoryCoroutineBlock(ssoLoginRepository)
        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)
        coVerify {
            ssoLogin.apiCoroutineBlock()
        }.wasInvoked(exactly = once)
    }

    private companion object {
        const val TEST_CODE = "code"
        const val TEST_COOKIE = "cookie"
        const val TEST_SUCCESS = "wire/success"
        const val TEST_ERROR = "wire/error"
        val TEST_SERVER_CONFIG = newServerConfig(1)
    }
}
