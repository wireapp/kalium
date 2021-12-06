package com.wire.kalium.api.tools.json.api.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImp
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class LoginApiTest : ApiTest {

    @Test
    fun givenAValidLoginRequest_whenCallingTheLoginEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val httpClient = mockHttpClient(
            VALID_LOGIN_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertQueryExist(QUERY_PERSIST)
                assertPathEqual(PATH_LOGIN)
            }
        )
        val loginApi: LoginApi = LoginApiImp(httpClient)

        val response = loginApi.emailLogin(VALID_LOGIN_REQUEST.serializableData, false)
        assertEquals(response.resultBody, VALID_LOGIN_RESPONSE.serializableData)

    }

    @Test
    fun givenAnInvalidLoginRequest_whenCallingTheLoginEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockHttpClient(
            ERROR_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Unauthorized
        )
        val loginApi: LoginApi = LoginApiImp(httpClient)

        val error = assertFailsWith<ClientRequestException> { loginApi.emailLogin(INVALID_LOGIN_REQUEST, false) }
        assertEquals(error.response.receive<ErrorResponse>(), ERROR_RESPONSE.serializableData)
    }


    private companion object {
        val VALID_LOGIN_REQUEST = LoginWithEmailRequestJson.valid
        val VALID_LOGIN_RESPONSE = LoginResponseJson.valid

        val INVALID_LOGIN_REQUEST = LoginWithEmailRequest("valid.test@email.com", "invalid_password", "label")

        val ERROR_RESPONSE = ErrorResponseJson.valid
        const val QUERY_PERSIST = "persist"
        const val PATH_LOGIN = "login"
    }
}
