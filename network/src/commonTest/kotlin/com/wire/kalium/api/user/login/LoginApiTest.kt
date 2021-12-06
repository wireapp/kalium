package com.wire.kalium.api.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImp
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import com.wire.kalium.network.api.user.login.LoginWithEmailResponse
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginApiTest : ApiTest {

    @Test
    fun givenAValidLoginRequest_whenCallingTheLoginEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val httpClient = mockHttpClient(
            KtxSerializer.json.encodeToString(VALID_LOGIN_RESPONSE),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertQueryExist(QUERY_PERSIST)
                assertPathEqual(PATH_LOGIN)
            }
        )
        val loginApi: LoginApi = LoginApiImp(httpClient)

        val response = loginApi.emailLogin(VALID_LOGIN_REQUEST, false)
        assertEquals(response.resultBody, VALID_LOGIN_RESPONSE)

    }

    @Test
    fun givenAnInvalidLoginRequest_whenCallingTheLoginEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockHttpClient(
            KtxSerializer.json.encodeToString(ERROR_RESPONSE),
            statusCode = HttpStatusCode.Unauthorized
        )
        val loginApi: LoginApi = LoginApiImp(httpClient)
        val error = assertFailsWith<ClientRequestException> { loginApi.emailLogin(INVALID_LOGIN_REQUEST, false) }
        assertEquals(error.response.receive<ErrorResponse>(), ERROR_RESPONSE)

    }


    private companion object {
        val VALID_LOGIN_REQUEST = LoginWithEmailRequest("valid.test@email.com", "valid_password", "label")
        val VALID_LOGIN_RESPONSE =
            LoginWithEmailResponse(userId = "user_id", expiresIn = 900, accessToken = "access_token", tokenType = "Bearer")

        val INVALID_LOGIN_REQUEST = LoginWithEmailRequest("valid.test@email.com", "invalid_password", "label")

        val ERROR_RESPONSE = ErrorResponse(401, "invalid credentials", "invalid_credentials")

        const val QUERY_PERSIST = "persist"
        const val PATH_LOGIN = "login"
    }
}
