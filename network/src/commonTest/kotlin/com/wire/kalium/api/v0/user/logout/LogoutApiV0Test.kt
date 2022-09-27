package com.wire.kalium.api.v0.user.logout

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.network.api.base.authenticated.logout.LogoutApi
import com.wire.kalium.network.api.v0.authenticated.LogoutApiV0
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
class LogoutApiV0Test : ApiTest {
    @Test
    fun givenAValidRegisterLogoutRequest_whenCallingTheRegisterLogoutEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val sessionManager = TEST_SESSION_NAMAGER
            val networkClient = mockAuthenticatedNetworkClient(
                "",
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertNoQueryParams()
                    assertPathEqual(PATH_LOGOUT)
                    assertHeaderEqual(HttpHeaders.Cookie, "zuid=${sessionManager.session().first.refreshToken}")
                }
            )
            val logout: LogoutApi = LogoutApiV0(networkClient, sessionManager)
            logout.logout()
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheLogoutEndpoint_theCorrectExceptionIsThrown() = runTest {
        val sessionManager = TEST_SESSION_NAMAGER

        val networkClient = mockAuthenticatedNetworkClient(
            ERROR_RESPONSE.rawJson,
            statusCode = HttpStatusCode.BadRequest
        )
        val logout: LogoutApi = LogoutApiV0(networkClient, sessionManager)
        val errorResponse = logout.logout()
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
