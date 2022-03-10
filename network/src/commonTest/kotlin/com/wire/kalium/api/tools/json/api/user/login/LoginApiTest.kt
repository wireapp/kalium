package com.wire.kalium.api.tools.json.api.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImpl
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class LoginApiTest : ApiTest {

    @Test
    fun givenAValidLoginRequest_whenCallingTheLoginEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val refreshToken = "415a5306-a476-41bc-af36-94ab075fd881"
        val httpClient = mockUnauthenticatedHttpClient(
            VALID_LOGIN_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertQueryExist(QUERY_PERSIST)
                assertPathEqual(PATH_LOGIN)
                assertHttps()
                assertHostEqual(TEST_HOST)
            },
            headers = mapOf("set-cookie" to "zuid=$refreshToken")
        )
        val expected = with(VALID_LOGIN_RESPONSE.serializableData) { SessionDTO(userIdValue = userId, accessToken = accessToken, tokenType = tokenType, refreshToken = refreshToken) }
        val loginApi: LoginApi = LoginApiImpl(httpClient)

        val response = loginApi.login(LOGIN_WITH_EMAIL_REQUEST.serializableData, false, TEST_HOST)
        assertTrue(response.isSuccessful())
        assertEquals(expected, response.value)
    }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheLoginEndpoint_thenExceptionIsPropagated() = runTest {
        val httpClient = mockUnauthenticatedHttpClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest
        )
        val loginApi: LoginApi = LoginApiImpl(httpClient)

        val errorResponse = loginApi.login(LOGIN_WITH_EMAIL_REQUEST.serializableData, false, TEST_HOST)
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)
    }

    private companion object {
        val LOGIN_WITH_EMAIL_REQUEST = LoginWithEmailRequestJson.validLoginWithEmail
        val VALID_LOGIN_RESPONSE = LoginResponseJson.valid
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
        const val QUERY_PERSIST = "persist"
        const val PATH_LOGIN = "/login"
        const val TEST_HOST = """test-https.wire.com"""
    }
}
