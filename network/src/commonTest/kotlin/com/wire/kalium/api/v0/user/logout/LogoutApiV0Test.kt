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

package com.wire.kalium.api.v0.user.logout

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.network.api.base.authenticated.logout.LogoutApi
import com.wire.kalium.network.api.v0.authenticated.LogoutApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class LogoutApiV0Test : ApiTest() {
    @Test
    fun givenAValidRegisterLogoutRequest_whenCallingTheRegisterLogoutEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val sessionManager = TEST_SESSION_MANAGER
            val networkClient = mockAuthenticatedNetworkClient(
                "",
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertNoQueryParams()
                    assertPathEqual(PATH_LOGOUT)
                    assertHeaderEqual(HttpHeaders.Cookie, "zuid=${sessionManager.session().refreshToken}")
                }
            )
            val logout: LogoutApi = LogoutApiV0(networkClient, sessionManager)
            logout.logout()
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheLogoutEndpoint_theCorrectExceptionIsThrown() = runTest {
        val sessionManager = TEST_SESSION_MANAGER

        val networkClient = mockAuthenticatedNetworkClient(
            ERROR_RESPONSE.rawJson,
            statusCode = HttpStatusCode.BadRequest
        )
        val logout: LogoutApi = LogoutApiV0(networkClient, sessionManager)
        val errorResponse = logout.logout()
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE.serializableData)
    }

    private companion object {
        const val PATH_LOGOUT = "/access/logout"
        const val TEST_COOKIE = "cookie"
        val ERROR_RESPONSE = ErrorResponseJson.valid
    }
}
