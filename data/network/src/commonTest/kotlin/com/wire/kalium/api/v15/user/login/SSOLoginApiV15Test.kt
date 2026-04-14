/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.api.v15.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND
import com.wire.kalium.network.api.base.unauthenticated.sso.SSOLoginApi
import com.wire.kalium.network.api.unauthenticated.sso.InitiateParam
import com.wire.kalium.network.api.v15.unauthenticated.SSOLoginApiV15
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class SSOLoginApiV15Test : ApiTest() {

    @Test
    fun givenV15_whenCallingInitiateSSOEndpointWithRedirectAndLabel_thenRequestConfiguredCorrectly() = runTest {
        val uuid = "uuid"
        val param =
            InitiateParam.WithRedirect(uuid = uuid, success = "wire://success", error = "wire://error", label = "shared-device")
        val expectedPathAndQuery =
            "$PATH_SSO_INITIATE/$uuid?label=shared-device&success_redirect=wire%3A%2F%2Fsuccess&error_redirect=wire%3A%2F%2Ferror"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertHead()
                assertPathAndQueryEqual(expectedPathAndQuery)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV15(networkClient)
        val actual = ssoApi.initiate(param)

        assertIs<NetworkResponse.Success<String>>(actual)
        assertEquals(expectedPathAndQuery, Url(actual.value).encodedPathAndQuery)
        assertEquals("${Url(TEST_BACKEND.links.api).protocolWithAuthority}$expectedPathAndQuery", actual.value)
    }

    @Test
    fun givenV15_whenCallingInitiateSSOEndpointWithNoRedirectAndLabel_thenRequestConfiguredCorrectly() = runTest {
        val uuid = "uuid"
        val param = InitiateParam.WithoutRedirect(uuid = uuid, label = "shared-device")
        val expectedPathAndQuery = "$PATH_SSO_INITIATE/$uuid?label=shared-device"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertHead()
                assertPathAndQueryEqual(expectedPathAndQuery)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV15(networkClient)
        val actual = ssoApi.initiate(param)

        assertIs<NetworkResponse.Success<String>>(actual)
        assertEquals(expectedPathAndQuery, Url(actual.value).encodedPathAndQuery)
        assertEquals("${Url(TEST_BACKEND.links.api).protocolWithAuthority}$expectedPathAndQuery", actual.value)
    }

    private companion object {
        const val PATH_SSO_INITIATE = "/sso/initiate-login"
    }
}
