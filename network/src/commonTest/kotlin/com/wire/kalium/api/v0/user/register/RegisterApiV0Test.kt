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

package com.wire.kalium.api.v0.user.register

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.model.ActivationRequestJson
import com.wire.kalium.model.RegisterAccountJson
import com.wire.kalium.model.RequestActivationCodeJson
import com.wire.kalium.model.UserDTOJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test

// test ignored until mocking is added to network
@Ignore
internal class RegisterApiV0Test : ApiTest() {

    @Test
    fun givenAValidEmail_whenRegisteringAccountWithEMail_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockUnauthenticatedNetworkClient(
            VALID_REGISTER_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual("/register")
                assertHttps()
                assertHostEqual(TEST_HOST)
                assertJsonBodyContent(VALID_PERSONAL_ACCOUNT_REQUEST.rawJson)
            }
        )
        TODO()
        // val registerApi: RegisterApi = RegisterApiImpl(networkClient)
        // val result = registerApi.register(VALID_PERSONAL_ACCOUNT_REQUEST.serializableData, TEST_HOST)

        // assertIs<NetworkResponse.Success<UserDTO>>(result)
        // assertEquals(VALID_REGISTER_RESPONSE.serializableData, result.value)
    }

    @Test
    fun givenRegistrationFail_whenRegisteringAccountWithEMMail_thenErrorIsPropagated() = runTest {
        val networkClient = mockUnauthenticatedNetworkClient(
            ERROR_RESPONSE.rawJson,
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual("/register")
                assertHttps()
                assertHostEqual(TEST_HOST)
                assertJsonBodyContent(VALID_PERSONAL_ACCOUNT_REQUEST.rawJson)
            }
        )
        TODO()
        // val registerApi: RegisterApi = RegisterApiImpl(networkClient)
        // val result = registerApi.register(VALID_PERSONAL_ACCOUNT_REQUEST.serializableData, TEST_HOST)

        // assertIs<NetworkResponse.Error>(result)
    }

    @Test
    fun givenAValidEmail_whenSendingActivationEmail_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual("/activate/send")
                assertHttps()
                assertHostEqual(TEST_HOST)
                assertJsonBodyContent(VALID_PERSONAL_ACCOUNT_REQUEST.rawJson)
            }
        )
        TODO()
        // val registerApi: RegisterApi = RegisterApiImpl(networkClient)
        // val result = registerApi.requestActivationCode(VALID_SEND_ACTIVATE_EMAIL.serializableData, TEST_HOST)

        // assertIs<NetworkResponse.Success<UserDTO>>(result)
        // assertIs<Unit>(result.value)
    }

    @Test
    fun givenSendActivationCodeFail_thenErrorIsPropagated() = runTest {
        val networkClient = mockUnauthenticatedNetworkClient(
            ERROR_RESPONSE.rawJson,
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual("/activate/send")
                assertHttps()
                assertHostEqual(TEST_HOST)
                assertJsonBodyContent(VALID_PERSONAL_ACCOUNT_REQUEST.rawJson)
            }
        )
        TODO()
        // val registerApi: RegisterApi = RegisterApiImpl(networkClient)
        // val result = registerApi.requestActivationCode(VALID_SEND_ACTIVATE_EMAIL.serializableData, TEST_HOST)

        // assertIs<NetworkResponse.Error>(result)
    }

    @Test
    fun givenAValidEmail_whenActivationEmailWIthCode_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual("/activate")
                assertHttps()
                assertHostEqual(TEST_HOST)
                assertJsonBodyContent(VALID_ACTIVATE_EMAIL.rawJson)
            }
        )
        TODO()
        // val registerApi: RegisterApi = RegisterApiImpl(networkClient)
        // val result = registerApi.activate(VALID_ACTIVATE_EMAIL.serializableData, TEST_HOST)

        // assertIs<NetworkResponse.Success<UserDTO>>(result)
        // assertIs<Unit>(result.value)
    }

    @Test
    fun givenActivationCodeFail_thenErrorIsPropagated() = runTest {
        val networkClient = mockUnauthenticatedNetworkClient(
            ERROR_RESPONSE.rawJson,
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual("/activate")
                assertHttps()
                assertHostEqual(TEST_HOST)
                assertJsonBodyContent(VALID_PERSONAL_ACCOUNT_REQUEST.rawJson)
            }
        )
        TODO()
        // val registerApi: RegisterApi = RegisterApiImpl(networkClient)
        // val result = registerApi.activate(VALID_ACTIVATE_EMAIL.serializableData, TEST_HOST)

        // assertIs<NetworkResponse.Error>(result)
    }

    private companion object {
        val VALID_PERSONAL_ACCOUNT_REQUEST = RegisterAccountJson.validPersonalAccountRegister
        val VALID_REGISTER_RESPONSE = UserDTOJson.valid
        val VALID_SEND_ACTIVATE_EMAIL = RequestActivationCodeJson.validActivateEmail
        val VALID_ACTIVATE_EMAIL = ActivationRequestJson.validActivateEmail
        val ERROR_RESPONSE = ErrorResponseJson.valid

        const val TEST_HOST = """test-https.wire.com"""

    }
}
