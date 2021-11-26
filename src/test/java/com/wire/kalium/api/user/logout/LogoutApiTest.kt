package com.wire.kalium.api.user.logout

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.ErrorResponse
import com.wire.kalium.api.NetworkResponse
import com.wire.kalium.tools.KtxSerializer
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LogoutApiTest : ApiTest {
    @Test
    fun `given a valid logout request, when calling the endpoint, request should return a successful NetworkResponse object`() =
        runBlocking {
            // Given
            val responseObj = Unit
            val arrangement = Arrangement()
                .withSuccessfulResponse(responseObj)
                .arrange()

            val mockHttpClient = arrangement.httpClient
            val mockedCookie = "mocked-cookie"

            // When
            val logoutApi: LogoutApi = LogoutApiImp(mockHttpClient)
            val response = logoutApi.logout(mockedCookie)

            // Then
            assertTrue(response is NetworkResponse.Success)
        }

    @Test
    fun `given an invalid logout request, when calling the logout endpoint, the correct server error exception is thrown`() = runBlocking {
        // Given
        val responseObj = ERROR_RESPONSE
        val arrangement = Arrangement()
            .withSuccessfulResponse(responseObj)
            .arrange()

        val mockHttpClient = arrangement.httpClient
        val mockedCookie = "mocked-cookie"

        // When
        val logoutApi: LogoutApi = LogoutApiImp(mockHttpClient)
        val response = logoutApi.logout(mockedCookie)

        // Then
        assertTrue(response is NetworkResponse.Success)
    }

    private inner class Arrangement {
        var expectedResponse = ""
        val httpClient = mockHttpClient(
            expectedResponse,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertQueryExist(QUERY_PERSIST)
                assertPathEqual(PATH_LOGOUT)
            }
        )

        fun withSuccessfulResponse(response: Any): Arrangement {
            expectedResponse = KtxSerializer.json.encodeToString(response)
            return this
        }

        fun arrange() = this
    }

    private companion object {

        val ERROR_RESPONSE = ErrorResponse(401, "invalid credentials", "invalid_credentials")

        const val QUERY_PERSIST = "persist"
        const val PATH_LOGOUT = "logout"
    }
}
