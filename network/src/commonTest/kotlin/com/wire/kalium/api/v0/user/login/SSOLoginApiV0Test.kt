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

package com.wire.kalium.api.v0.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND
import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.mocks.mocks.client.TokenMocks
import com.wire.kalium.mocks.mocks.user.UserMocks
import com.wire.kalium.network.api.base.unauthenticated.sso.SSOLoginApi
import com.wire.kalium.network.api.model.AuthenticationResultDTO
import com.wire.kalium.network.api.unauthenticated.sso.InitiateParam
import com.wire.kalium.network.api.v0.unauthenticated.SSOLoginApiV0
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class SSOLoginApiV0Test : ApiTest() {

    @Test
    fun givenBEResponseSuccess_whenCallingInitiateSSOEndpointWithNoRedirect_thenRequestConfiguredCorrectly() = runTest {
        val uuid = "uuid"
        val param = InitiateParam.WithoutRedirect(uuid)
        val expectedPath = "$PATH_SSO_INITIATE/$uuid"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertHead()
                assertNoQueryParams()
                assertPathEqual(expectedPath)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.initiate(param)

        assertIs<NetworkResponse.Success<String>>(actual)
        assertEquals(expectedPath, Url(actual.value).encodedPathAndQuery)
        assertEquals("${Url(TEST_BACKEND.links.api).protocolWithAuthority}$expectedPath", actual.value)
    }

    @Test
    fun givenBEResponseSuccess_whenCallingInitiateSSOEndpointWithRedirect_thenRequestConfiguredCorrectly() = runTest {
        val uuid = "uuid"
        val param =
            InitiateParam.WithRedirect(uuid = uuid, success = "wire://success", error = "wire://error")
        val expectedPathAndQuery =
            "$PATH_SSO_INITIATE/$uuid?success_redirect=wire%3A%2F%2Fsuccess&error_redirect=wire%3A%2F%2Ferror"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertHead()
                assertPathAndQueryEqual(expectedPathAndQuery)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.initiate(param)

        assertIs<NetworkResponse.Success<String>>(actual)
        assertEquals(expectedPathAndQuery, Url(actual.value).encodedPathAndQuery)
        assertEquals("${Url(TEST_BACKEND.links.api).protocolWithAuthority}$expectedPathAndQuery", actual.value)
    }

    @Test
    fun givenBEResponseSuccess_whenCallingFinalizeSSOEndpointWithRedirect_thenRequestConfiguredCorrectly() = runTest {
        val cookie = "cookie"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertHeaderEqual(HttpHeaders.Cookie, "zuid=$cookie")
                assertPathEqual(PATH_SSO_FINALIZE)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.finalize(cookie)

        assertIs<NetworkResponse.Success<String>>(actual)
    }

    @Test
    fun givenBEResponseSuccess_whenFetchingAuthToken_thenTheRefreshTokenIsClean() = runTest {
        val cookie = "zuid=cookie"
        val authResponse = TokenMocks.accessToken
        val selfResponse = UserMocks.selfUser
        val networkClient = mockUnauthenticatedNetworkClient(
            listOf(
                TestRequestHandler(
                    path = PATH_ACCESS,
                    authResponse.toJsonString(),
                    statusCode = HttpStatusCode.OK,
                    assertion = {
                        assertGet()
                        assertHeaderEqual(HttpHeaders.Cookie, cookie)
                        assertPathEqual(PATH_ACCESS)
                    }
                ),
                TestRequestHandler(
                    path = PATH_SELF,
                    selfResponse.toJsonString(),
                    statusCode = HttpStatusCode.OK,
                    assertion = {
                        assertGet()
                        assertPathEqual(PATH_SELF)
                    }
                )
            )
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.provideLoginSession(cookie)

        assertIs<NetworkResponse.Success<AuthenticationResultDTO>>(actual)
        assertEquals(cookie.removePrefix("zuid="), actual.value.sessionDTO.refreshToken)
        assertEquals(authResponse.value, actual.value.sessionDTO.accessToken)
        assertEquals(authResponse.tokenType, actual.value.sessionDTO.tokenType)
        assertEquals(selfResponse.id, actual.value.sessionDTO.userId)
        assertEquals(selfResponse, actual.value.userDTO)
    }

    @Test
    fun cookieIsMissingZuidToke_whenFetchingAuthToken_thenReturnError() = runTest {
        val cookie = "cookie"
        val authResponse = TokenMocks.accessToken
        val networkClient = mockUnauthenticatedNetworkClient(
            listOf(
                TestRequestHandler(
                    path = PATH_ACCESS,
                    authResponse.toJsonString(),
                    statusCode = HttpStatusCode.OK
                )
            )
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.provideLoginSession(cookie)

        assertIs<NetworkResponse.Error>(actual)
        assertEquals(CustomErrors.MISSING_REFRESH_TOKEN, actual)
    }

    private companion object {
        const val PATH_SSO_INITIATE = "/sso/initiate-login"
        const val PATH_SSO_FINALIZE = "/sso/finalize-login"
        const val PATH_ACCESS = "/access"
        const val PATH_SELF = "/self"

    }

}
