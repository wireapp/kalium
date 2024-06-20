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
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.mocks.responses.AccessTokenDTOJson
import com.wire.kalium.mocks.responses.LoginWithEmailRequestJson
import com.wire.kalium.mocks.responses.UserDTOJson
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.api.base.unauthenticated.login.LoginApi
import com.wire.kalium.network.api.v0.unauthenticated.LoginApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.util.serialization.toJsonElement
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class LoginApiV0Test : ApiTest() {

    @Test
    fun givenAValidLoginRequest_whenCallingTheLoginEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val expectedLoginRequest = TestRequestHandler(
            path = PATH_LOGIN,
            responseBody = VALID_ACCESS_TOKEN_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertQueryExist(QUERY_PERSIST)
                assertHttps()
                assertJson()
                val verificationCode = this.body.toJsonElement().jsonObject["verification_code"]?.jsonPrimitive?.content
                assertEquals(LOGIN_WITH_EMAIL_REQUEST.serializableData.verificationCode, verificationCode)
            },
            headers = mapOf("set-cookie" to "zuid=$refreshToken")
        )
        val expectedSelfResponse = ApiTest.TestRequestHandler(
            path = PATH_SELF,
            responseBody = VALID_SELF_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertHttps()
                assertJson()
            }
        )
        val networkClient = mockUnauthenticatedNetworkClient(
            listOf(expectedLoginRequest, expectedSelfResponse)
        )
        val expected = with(VALID_ACCESS_TOKEN_RESPONSE.serializableData) {
            SessionDTO(
                userId = VALID_SELF_RESPONSE.serializableData.id,
                accessToken = value,
                tokenType = tokenType,
                refreshToken = refreshToken,
                cookieLabel = LOGIN_WITH_EMAIL_REQUEST.serializableData.label
            ) to userDTO
        }
        val loginApi: LoginApi = LoginApiV0(networkClient)

        val response = loginApi.login(LOGIN_WITH_EMAIL_REQUEST.serializableData, false)
        assertTrue(response.isSuccessful(), message = response.toString())
        assertEquals(expected, response.value)
    }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheLoginEndpoint_thenExceptionIsPropagated() = runTest {
        val networkClient = mockUnauthenticatedNetworkClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest
        )
        val loginApi: LoginApi = LoginApiV0(networkClient)

        val errorResponse = loginApi.login(LOGIN_WITH_EMAIL_REQUEST.serializableData, false)
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)
    }

    @Test
    fun givenLoginRequestSuccessAndSelfInfoFail_thenExceptionIsPropagated() = runTest {
        val expectedLoginRequest = ApiTest.TestRequestHandler(
            path = PATH_LOGIN,
            responseBody = VALID_ACCESS_TOKEN_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            headers = mapOf("set-cookie" to "zuid=$refreshToken")
        )
        val expectedSelfResponse = ApiTest.TestRequestHandler(
            path = PATH_SELF,
            responseBody = ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest
        )
        val networkClient = mockUnauthenticatedNetworkClient(
            listOf(expectedLoginRequest, expectedSelfResponse)
        )
        val loginApi: LoginApi = LoginApiV0(networkClient)

        val errorResponse = loginApi.login(LOGIN_WITH_EMAIL_REQUEST.serializableData, false)
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)

    }

    private companion object {
        const val refreshToken = "415a5306-a476-41bc-af36-94ab075fd881"
        val userID = QualifiedID("user_id", "user.domain.io")
        val accessTokenDto = AccessTokenDTO(
            userId = userID.value,
            value = "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939." +
                    "t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
            expiresIn = 900,
            tokenType = "Bearer"
        )
        val userDTO = SelfUserDTO(
            id = userID,
            name = "user_name_123",
            accentId = 2,
            assets = listOf(),
            deleted = null,
            email = null,
            handle = null,
            service = null,
            teamId = null,
            expiresAt = "",
            nonQualifiedId = "",
            locale = "",
            managedByDTO = null,
            phone = null,
            ssoID = null,
            supportedProtocols = null
        )
        val VALID_ACCESS_TOKEN_RESPONSE = AccessTokenDTOJson.createValid(accessTokenDto)
        val VALID_SELF_RESPONSE = UserDTOJson.createValid(userDTO)

        val LOGIN_WITH_EMAIL_REQUEST = LoginWithEmailRequestJson.validLoginWithEmail
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
        const val QUERY_PERSIST = "persist"
        const val PATH_LOGIN = "/login"
        const val PATH_SELF = "/self"
    }
}
