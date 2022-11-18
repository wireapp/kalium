package com.wire.kalium.api.common

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.json.model.testCredentials
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.model.RefreshTokenDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.api.v0.authenticated.AccessTokenApiV0
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.session.installAuth
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    @Test
    fun givenClientWithAuth_whenServerReturns401_thenShouldTryAgainWithNewToken() = runTest {
        val sessionManager = createFakeSessionManager()
        var callCount = 0
        var didFail = false
        val mockEngine = MockEngine() {
            callCount++
            // Fail only the first time, so the test can
            // proceed when sessionManager is called again
            if (!didFail) {
                assertEquals("Bearer ${testCredentials.accessToken}", it.headers[HttpHeaders.Authorization])
                didFail = true
                respondError(status = HttpStatusCode.Unauthorized)
            } else {
                assertEquals("Bearer $UPDATED_ACCESS_TOKEN", it.headers[HttpHeaders.Authorization])
                respondOk()
            }
        }

        val client = HttpClient(mockEngine) {
            installAuth(sessionManager) { httpClient -> AccessTokenApiV0(httpClient) }
            expectSuccess = false
        }

        client.get(TEST_BACKEND_CONFIG.links.api)
        assertEquals(2, callCount)
    }

    @Test
    fun givenClientWithAuth_whenServerReturnsOK_thenShouldNotAddBearerWWWAuthHeader() = runTest {
        val sessionManager = createFakeSessionManager()

        val mockEngine = MockEngine() {
            respondOk()
        }

        val client = HttpClient(mockEngine) {
            installAuth(sessionManager) { httpClient -> AccessTokenApiV0(httpClient) }
            expectSuccess = false
        }

        val response = client.get(TEST_BACKEND_CONFIG.links.api)

        assertNull(response.headers[HttpHeaders.WWWAuthenticate])
    }

    private companion object {
        const val UPDATED_ACCESS_TOKEN = "new access token"
    }

    private fun createFakeSessionManager() = object : SessionManager {
        override suspend fun session(): SessionDTO = testCredentials
        override fun serverConfig(): ServerConfigDTO = TEST_BACKEND_CONFIG
        override suspend fun updateToken(
            accessTokenApi: AccessTokenApi,
            oldAccessToken: String,
            oldRefreshToken: String
        ): SessionDTO? = testCredentials.copy(accessToken = UPDATED_ACCESS_TOKEN)

        override suspend fun updateLoginSession(newAccessTokeDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO? =
            testCredentials

        override fun proxyCredentials(): ProxyCredentialsDTO? = ProxyCredentialsDTO("username", "password")
    }
}
