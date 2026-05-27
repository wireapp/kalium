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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.network.api.base.unauthenticated.domainLookup.DomainLookupApi
import com.wire.kalium.network.api.base.unauthenticated.sso.SSOLoginApi
import com.wire.kalium.network.api.unauthenticated.domainLookup.DomainLookupResponse
import com.wire.kalium.network.api.unauthenticated.sso.InitiateParam
import com.wire.kalium.network.api.unauthenticated.sso.SSOCodeResponse
import com.wire.kalium.network.api.unauthenticated.sso.SSOSettingsResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SSOLoginRepositoryTest {

    val ssoLogin = mock<SSOLoginApi>(mode = MockMode.autoUnit)

    val domainLookup = mock<DomainLookupApi>(mode = MockMode.autoUnit)

    private lateinit var ssoLoginRepository: SSOLoginRepository

    @BeforeTest
    fun setup() {
        ssoLoginRepository = SSOLoginRepositoryImpl(ssoLogin, domainLookup)
    }

    @Test
    fun givenApiRequestSuccess_whenInitiatingWithoutRedirects_thenSuccessIsPropagated() = runTest {
        val expected = "wire/response"
        everySuspend {
            ssoLogin.initiate(InitiateParam.WithoutRedirect(TEST_CODE))
        }.returns(NetworkResponse.Success(expected, mapOf(), 200))

        val actual = ssoLoginRepository.initiate(TEST_CODE)

        assertSuccessIsPropagated(expected, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.initiate(InitiateParam.WithoutRedirect(TEST_CODE))
        }
    }

    @Test
    fun givenApiRequestSuccess_whenInitiatingWithRedirects_thenSuccessIsPropagated() = runTest {
        val expected = "wire/response"
        val initiateParam = InitiateParam.WithRedirect(TEST_SUCCESS, TEST_ERROR, TEST_CODE)
        everySuspend {
            ssoLogin.initiate(initiateParam)
        }.returns(NetworkResponse.Success(expected, mapOf(), 200))

        val actual = ssoLoginRepository.initiate(TEST_CODE, TEST_SUCCESS, TEST_ERROR)

        assertSuccessIsPropagated(expected, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.initiate(initiateParam)
        }
    }

    @Test
    fun givenApiRequestFail_whenInitiating_thenNetworkFailureIsPropagated() = runTest {
        everySuspend {
            ssoLogin.initiate(InitiateParam.WithoutRedirect(TEST_CODE))
        }.returns(NetworkResponse.Error(TestNetworkException.generic))

        val actual = ssoLoginRepository.initiate(TEST_CODE)

        assertNetworkFailureIsPropagated(TestNetworkException.generic, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.initiate(InitiateParam.WithoutRedirect(TEST_CODE))
        }
    }

    @Test
    fun givenApiRequestSuccess_whenFinalizing_thenSuccessIsPropagated() = runTest {
        val expected = "wire/response"
        everySuspend {
            ssoLogin.finalize(TEST_COOKIE)
        }.returns(NetworkResponse.Success(expected, mapOf(), 200))

        val actual = ssoLoginRepository.finalize(TEST_COOKIE)

        assertSuccessIsPropagated(expected, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.finalize(TEST_COOKIE)
        }
    }

    @Test
    fun givenApiRequestFail_whenFinalizing_thenNetworkFailureIsPropagated() = runTest {
        everySuspend {
            ssoLogin.finalize(TEST_COOKIE)
        }.returns(NetworkResponse.Error(TestNetworkException.generic))

        val actual = ssoLoginRepository.finalize(TEST_COOKIE)

        assertNetworkFailureIsPropagated(TestNetworkException.generic, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.finalize(TEST_COOKIE)
        }
    }

    @Test
    fun givenApiRequestSuccess_whenRequestingMetaData_thenSuccessIsPropagated() = runTest {
        val expected = "wire/response"
        everySuspend {
            ssoLogin.metaData()
        }.returns(NetworkResponse.Success(expected, mapOf(), 200))

        val actual = ssoLoginRepository.metaData()

        assertSuccessIsPropagated(expected, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.metaData()
        }
    }

    @Test
    fun givenApiRequestFail_whenRequestingMetaData_thenNetworkFailureIsPropagated() = runTest {
        everySuspend {
            ssoLogin.metaData()
        }.returns(NetworkResponse.Error(TestNetworkException.generic))

        val actual = ssoLoginRepository.metaData()

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(TestNetworkException.generic, actual.value.kaliumException)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.metaData()
        }
    }

    @Test
    fun givenApiRequestSuccess_whenRequestingSettings_thenSuccessIsPropagated() = runTest {
        val expected = SSOSettingsResponse("default_code")
        everySuspend {
            ssoLogin.settings()
        }.returns(NetworkResponse.Success(expected, mapOf(), 200))

        val actual = ssoLoginRepository.settings()

        assertSuccessIsPropagated(expected, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.settings()
        }
    }

    @Test
    fun givenApiRequestFail_whenRequestingSettings_thenNetworkFailureIsPropagated() = runTest {
        everySuspend {
            ssoLogin.settings()
        }.returns(NetworkResponse.Error(TestNetworkException.generic))

        val actual = ssoLoginRepository.settings()

        assertNetworkFailureIsPropagated(TestNetworkException.generic, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.settings()
        }
    }

    @Test
    fun givenApiRequestSuccess_whenGettingSsoCodeByEmail_thenSuccessIsPropagated() = runTest {
        everySuspend {
            ssoLogin.getByEmail(TEST_EMAIL)
        }.returns(NetworkResponse.Success(SSOCodeResponse(TEST_CODE), mapOf(), 200))

        val actual = ssoLoginRepository.getByEmail(TEST_EMAIL)

        assertIs<Either.Right<String?>>(actual)
        assertEquals(TEST_CODE, actual.value)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.getByEmail(TEST_EMAIL)
        }
    }

    @Test
    fun givenApiRequestFail_whenGettingSsoCodeByEmail_thenNetworkFailureIsPropagated() = runTest {
        everySuspend {
            ssoLogin.getByEmail(TEST_EMAIL)
        }.returns(NetworkResponse.Error(TestNetworkException.generic))

        val actual = ssoLoginRepository.getByEmail(TEST_EMAIL)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(TestNetworkException.generic, actual.value.kaliumException)
        verifySuspend(VerifyMode.exactly(1)) {
            ssoLogin.getByEmail(TEST_EMAIL)
        }
    }

    @Test
    fun givenDomainLookupSuccess_thenSuccesIsPropagated() = runTest {
        val domain = "test.com"
        val networkResponse = DomainLookupResponse(
            configJsonUrl = "https://test.com/config.json",
            webappWelcomeUrl = "https://test.com/welcome"
        )

        everySuspend {
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

        verifySuspend(VerifyMode.exactly(1)) {
            domainLookup.lookup(any())
        }
    }

    private fun <T : Any> assertSuccessIsPropagated(
        expected: T,
        actual: Either<NetworkFailure, T>
    ) {
        assertIs<Either.Right<T>>(actual)
        assertEquals(expected, actual.value)
    }

    private fun <T : Any> assertNetworkFailureIsPropagated(
        expected: KaliumException,
        actual: Either<NetworkFailure, T>
    ) {
        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)
    }

    private companion object {
        const val TEST_CODE = "code"
        const val TEST_COOKIE = "cookie"
        const val TEST_EMAIL = "user@example.com"
        const val TEST_SUCCESS = "wire/success"
        const val TEST_ERROR = "wire/error"
        val TEST_SERVER_CONFIG = newServerConfig(1)
    }
}
