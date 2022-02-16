package com.wire.kalium.api.tools.json.api.user.logout

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.user.logout.LogoutApi
import com.wire.kalium.network.api.user.logout.LogoutImpl
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
            val httpClient = mockAuthenticatedHttpClient(
                "",
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertNoQueryParams()
                    assertPathEqual(PATH_LOGOUT)
                    assertHeaderExist(HttpHeaders.Cookie)
                }
            )
            val logout: LogoutApi = LogoutImpl(httpClient)
            logout.logout(TEST_COOKIE)
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheLogoutEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            ERROR_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Unauthorized
        )
        val logout: LogoutApi = LogoutImpl(httpClient)
        val errorResponse = logout.logout(TEST_COOKIE)
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
