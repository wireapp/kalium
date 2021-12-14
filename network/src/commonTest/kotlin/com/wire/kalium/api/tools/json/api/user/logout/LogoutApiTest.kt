package com.wire.kalium.api.tools.json.api.user.logout

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.api.user.client.RegisterClientResponseJson
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.user.logout.LogoutApi
import com.wire.kalium.network.api.user.logout.LogoutImp
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
class LogoutApiTest : ApiTest {
    @Test
    fun givenAValidRegisterLogoutRequest_whenCallingTheRegisterLogoutEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val httpClient = mockHttpClient(
                VALID_REGISTER_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertNoQueryParams()
                    assertPathEqual(PATH_LOGOUT)
                    assertHeaderExist(HttpHeaders.Cookie)
                }
            )
            val logout: LogoutApi = LogoutImp(httpClient)
            logout.logout(TEST_COOKIE)
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheLogoutEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockHttpClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.Unauthorized
        )
        val logout: LogoutApi = LogoutImp(httpClient)
        val errorResponse = logout.logout(TEST_COOKIE)
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)
    }

    private companion object {
        const val PATH_LOGOUT = "/access/logout"
        const val TEST_COOKIE = "cookie"
        val VALID_REGISTER_CLIENT_RESPONSE = RegisterClientResponseJson.valid
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
    }
}
