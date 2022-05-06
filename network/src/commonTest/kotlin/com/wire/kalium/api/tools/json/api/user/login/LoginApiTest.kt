package com.wire.kalium.api.tools.json.api.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.api.user.register.UserDTOJson
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImpl
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class LoginApiTest : ApiTest {

    @Test
    fun givenAValidLoginRequest_whenCallingTheLoginEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val expectedLoginRequest = ApiTest.TestRequestHandler(
            path = PATH_LOGIN,
            responseBody = VALID_ACCESS_TOKEN_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertQueryExist(QUERY_PERSIST)
                assertHttps()
                assertJson()
                assertHostEqual(TEST_HOST)
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
                assertHostEqual(TEST_HOST)
            }
        )
        val httpClient = mockUnauthenticatedHttpClient(
            listOf(expectedLoginRequest, expectedSelfResponse)
        )
        val expected = with(VALID_ACCESS_TOKEN_RESPONSE.serializableData) {
            SessionDTO(
                userId = VALID_SELF_RESPONSE.serializableData.id,
                accessToken = value,
                tokenType = tokenType,
                refreshToken = refreshToken
            )
        }
        val loginApi: LoginApi = LoginApiImpl(httpClient)

        val response = loginApi.login(LOGIN_WITH_EMAIL_REQUEST.serializableData, false, TEST_HOST)
        assertTrue(response.isSuccessful(), message = response.toString())
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
        val httpClient = mockUnauthenticatedHttpClient(
            listOf(expectedLoginRequest, expectedSelfResponse)
        )
        val loginApi: LoginApi = LoginApiImpl(httpClient)

        val errorResponse = loginApi.login(LOGIN_WITH_EMAIL_REQUEST.serializableData, false, TEST_HOST)
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)

    }

    private companion object {
        val refreshToken = "415a5306-a476-41bc-af36-94ab075fd881"
        val userID = QualifiedID("user_id", "user.domain.io")
        val accessTokenDto = AccessTokenDTO(
            userId = userID.value,
            value = "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939." +
                    "t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
            expiresIn = 900,
            tokenType = "Bearer"
        )
        val userDTO = UserDTO(
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
            ssoID = null
        )
        val VALID_ACCESS_TOKEN_RESPONSE = AccessTokenDTOJson.createValid(accessTokenDto)
        val VALID_SELF_RESPONSE = UserDTOJson.createValid(userDTO)

        val LOGIN_WITH_EMAIL_REQUEST = LoginWithEmailRequestJson.validLoginWithEmail
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
        const val QUERY_PERSIST = "persist"
        const val PATH_LOGIN = "/login"
        const val PATH_SELF = "/self"
        const val TEST_HOST = """https://test-https.wire.com"""
    }
}
