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

package com.wire.kalium.api.v0.user.self

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.mocks.responses.UserDTOJson
import com.wire.kalium.network.api.base.model.SupportedProtocolDTO
import com.wire.kalium.network.api.v0.authenticated.SelfApiV0
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
internal class SelfApiV0Test : ApiTest() {
    @Test
    fun givenAValidRegisterLogoutRequest_whenCallingTheRegisterLogoutEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                VALID_SELF_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertGet()
                    assertNoQueryParams()
                    assertPathEqual(PATH_SELF)
                }
            )
            val selfApi = SelfApiV0(networkClient, TEST_SESSION_MANAGER)
            val response = selfApi.getSelfInfo()
            assertTrue(response.isSuccessful())
            assertEquals(response.value, VALID_SELF_RESPONSE.serializableData)
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheGetSelfEndpoint_thenExceptionIsPropagated() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest
        )
        val selfApi = SelfApiV0(networkClient, TEST_SESSION_MANAGER)
        val response = selfApi.getSelfInfo()
        assertFalse(response.isSuccessful())
        assertTrue(response.kException is KaliumException.InvalidRequestError)
        assertEquals((response.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)
    }

    @Test
    fun givenUpdateEmailSuccess_whenChangingSelfEmail_thenSuccessIsReturned() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.Accepted,
            assertion = {
                assertPut()
                assertNoQueryParams()
                assertHeaderEqual(HttpHeaders.Cookie, "zuid=${TEST_SESSION_MANAGER.session().refreshToken}")
                assertPathEqual("access/self/email")
            }
        )
        val selfApi = SelfApiV0(networkClient, TEST_SESSION_MANAGER)
        selfApi.updateEmailAddress("new Email").also {
            assertTrue(it.isSuccessful())
            assertTrue(it.value)
        }
    }

    @Test
    fun givenUpdateEmailSuccessWith204HttpCode_whenChangingSelfEmail_thenFalse() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.NoContent,
            assertion = {
                assertPut()
                assertNoQueryParams()
                assertHeaderEqual(HttpHeaders.Cookie, "zuid=${TEST_SESSION_MANAGER.session().refreshToken}")
                assertPathEqual("access/self/email")
            }
        )
        val selfApi = SelfApiV0(networkClient, TEST_SESSION_MANAGER)
        selfApi.updateEmailAddress("new Email").also {
            assertTrue(it.isSuccessful())
            assertFalse(it.value)
        }
    }

    @Test
    fun givenUpdateEmailFailure_whenChangingSelfEmail_thenFailureIsReturned() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertPut()
                assertNoQueryParams()
                assertPathEqual("access/self/email")
            }
        )
        val selfApi = SelfApiV0(networkClient, TEST_SESSION_MANAGER)
        selfApi.updateEmailAddress("new Email").also {
            assertFalse(it.isSuccessful())
            assertTrue(it.kException is KaliumException.InvalidRequestError)
            assertEquals((it.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)
        }
    }

    @Test
    fun givenRequest_whenUpdatingSupportedProtocols_thenRequestShouldFail() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(responseBody = "", statusCode = HttpStatusCode.OK)
        val selfApi = SelfApiV0(networkClient, TEST_SESSION_MANAGER)
        val response = selfApi.updateSupportedProtocols(listOf(SupportedProtocolDTO.PROTEUS))

        assertFalse(response.isSuccessful())
    }

    private companion object {
        const val PATH_SELF = "/self"
        val VALID_SELF_RESPONSE = UserDTOJson.valid
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
    }
}
