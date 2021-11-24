package com.wire.kalium.api.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.ErrorResponse
import com.wire.kalium.tools.KtxSerializer
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginApiTest : ApiTest {
    @Test
    fun `given a valid login request, when calling the login endpoint, the request should be configured courtly`() {
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
        runBlocking {
            val response = loginApi.emailLogin(VALID_LOGIN_REQUEST, false)
            assertEquals(response, VALID_LOGIN_RESPONSE)
        }
    }

    @Test
    fun `given an invalid login request, when calling the login endpoint, the correct exception is thrown`() {
        val httpClient = mockHttpClient(
                KtxSerializer.json.encodeToString(ERROR_RESPONSE),
                statusCode = HttpStatusCode.Unauthorized
        )
        val loginApi: LoginApi = LoginApiImp(httpClient)
        runBlocking {
            val error = assertFailsWith<ClientRequestException> { loginApi.emailLogin(INVALID_LOGIN_REQUEST, false) }
            assertEquals(error.response.receive<ErrorResponse>(), ERROR_RESPONSE)
        }
    }


    private companion object {
        val VALID_LOGIN_REQUEST = LoginWithEmailRequest("valid.test@email.com", "valid_password", "label")
        val VALID_LOGIN_RESPONSE = LoginWithEmailResponse(userId = "user_id", expiresIn = 900, accessToken = "access_token", tokenType = "Bearer")

        val INVALID_LOGIN_REQUEST = LoginWithEmailRequest("valid.test@email.com", "invalid_password", "label")

        val ERROR_RESPONSE = ErrorResponse(401, "invalid credentials", "invalid_credentials")

        const val QUERY_PERSIST = "persist"
        const val PATH_LOGIN = "login"
    }
}
