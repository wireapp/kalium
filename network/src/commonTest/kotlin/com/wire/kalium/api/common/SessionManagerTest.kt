package com.wire.kalium.api.common

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.json.model.testCredentials
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.RefreshTokenDTO
import com.wire.kalium.network.api.base.model.SessionDTO
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionManagerTest {

    @Test
    fun givenClientWithAuth_whenServerReturns401_thenShouldAddBearerWWWAuthHeader() = runTest {
        val sessionManager = createFakeSessionManager()

        var didFail = false
        val mockEngine = MockEngine() {
            // Fail only the first time, so the test can
            // proceed when sessionManager is called again
            if (!didFail) {
                didFail = true
                respondError(status = HttpStatusCode.Unauthorized)
            } else {
                respondOk()
            }
        }

        val client = HttpClient(mockEngine) {
            installAuth(sessionManager)
            expectSuccess = false
        }

        val response = client.get(TEST_BACKEND_CONFIG.links.api)

        assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun givenClientWithAuth_whenServerReturnsOK_thenShouldNotAddBearerWWWAuthHeader() = runTest {
        val sessionManager = createFakeSessionManager()

        val mockEngine = MockEngine() {
            respondOk()
        }

        val client = HttpClient(mockEngine) {
            installAuth(sessionManager)
            expectSuccess = false
        }

        val response = client.get(TEST_BACKEND_CONFIG.links.api)

        assertNull(response.headers[HttpHeaders.WWWAuthenticate])
    }

    private fun createFakeSessionManager() = object : SessionManager {
        override fun session(): Pair<SessionDTO, ServerConfigDTO.Links> = testCredentials to TEST_BACKEND_CONFIG.links

        override fun updateLoginSession(newAccessTokenDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
            testCredentials

        override suspend fun onSessionExpired() = TODO("Not yet implemented")

        override suspend fun onClientRemoved() = TODO("Not yet implemented")
    }
}
