/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.api.common

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.json.model.testCredentials
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.api.v0.authenticated.AccessTokenApiV0
import com.wire.kalium.network.api.v0.authenticated.AssetApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.networkContainer.KaliumUserAgentProvider
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.session.installAuth
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests how our [SessionManager] integrates with the internals of Ktor.
 * For example, making sure that when we throw an exception during token refresh,
 * Ktor will catch it, and we will be able to return a [NetworkResponse.Error] with the exception.
 */
class SessionManagerIntegrationTest {

    @Test
    fun givenClientWithAuth_whenServerReturns401_thenShouldTryAgainWithNewToken() = runTest {
        val sessionManager = createFakeSessionManager()

        val loadToken: suspend () -> BearerTokens? = {
            val session = sessionManager.session()
            BearerTokens(accessToken = session.accessToken, refreshToken = session.refreshToken)
        }

        val refreshToken: suspend RefreshTokensParams.() -> BearerTokens? = {
            val newSession = sessionManager.updateToken(
                accessTokenApi = AccessTokenApiV0(client),
                oldAccessToken = oldTokens!!.accessToken,
                oldRefreshToken = oldTokens!!.refreshToken
            )
            newSession.let {
                BearerTokens(accessToken = it.accessToken, refreshToken = it.refreshToken)
            }
        }

        val bearerAuthProvider = BearerAuthProvider(refreshToken, loadToken, { true }, null)

        var callCount = 0
        var didFail = false
        val mockEngine = MockEngine {
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
            installAuth(bearerAuthProvider)
            expectSuccess = false
        }

        client.get(TEST_BACKEND_CONFIG.links.api)
        assertEquals(2, callCount)
    }

    @Test
    fun givenClientWithAuth_whenServerReturnsOK_thenShouldNotAddBearerWWWAuthHeader() = runTest {
        val sessionManager = createFakeSessionManager()

        val loadToken: suspend () -> BearerTokens? = {
            val session = sessionManager.session()
            BearerTokens(accessToken = session.accessToken, refreshToken = session.refreshToken)
        }

        val refreshToken: suspend RefreshTokensParams.() -> BearerTokens? = {
            val newSession = sessionManager.updateToken(
                accessTokenApi = AccessTokenApiV0(client),
                oldAccessToken = oldTokens!!.accessToken,
                oldRefreshToken = oldTokens!!.refreshToken
            )
            newSession.let {
                BearerTokens(accessToken = it.accessToken, refreshToken = it.refreshToken)
            }
        }

        val bearerAuthProvider = BearerAuthProvider(refreshToken, loadToken, { true }, null)

        val mockEngine = MockEngine {
            respondOk()
        }

        val client = HttpClient(mockEngine) {
            installAuth(bearerAuthProvider)
            expectSuccess = false
        }

        val response = client.get(TEST_BACKEND_CONFIG.links.api)

        assertNull(response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun givenClientWithAuth_whenServerReturns401ForAssetDownload_thenShouldTryAgainWithNewToken() = runTest {
        // setup the user agent provider
        KaliumUserAgentProvider.setUserAgent("KaliumTest")

        val sessionManager = createFakeSessionManager()

        val loadToken: suspend () -> BearerTokens? = {
            val session = sessionManager.session()
            BearerTokens(accessToken = session.accessToken, refreshToken = session.refreshToken)
        }

        val refreshToken: suspend RefreshTokensParams.() -> BearerTokens? = {
            val newSession = sessionManager.updateToken(
                AccessTokenApiV0(client),
                oldTokens!!.accessToken,
                oldTokens!!.refreshToken
            )
            newSession.let {
                BearerTokens(accessToken = it.accessToken, refreshToken = it.refreshToken)
            }
        }

        val bearerAuthProvider = BearerAuthProvider(refreshToken, loadToken, { true }, null)

        var callCount = 0
        var didFail = false
        val mockEngine = MockEngine {
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

        val client = AuthenticatedNetworkClient(
            mockEngine,
            sessionManager.serverConfig(),
            bearerAuthProvider,
            kaliumLogger,
            false
        )
        val assetApi = AssetApiV0(client)
        val kaliumFileSystem: FileSystem = FakeFileSystem()
        val tempPath = "some-dummy-path".toPath()
        val tempOutputSink = kaliumFileSystem.sink(tempPath)

        assetApi.downloadAsset("asset_id", "asset_domain", null, tempFileSink = tempOutputSink)
        assertEquals(2, callCount)
    }

    @Test
    fun givenRefreshTokenThrows_whenServerSignalTokenRefreshIsNeeded_thenShouldReturnFailure() = runTest {
        KaliumUserAgentProvider.setUserAgent("KaliumTest")
        val sessionManager = createFakeSessionManager()

        val loadToken: suspend () -> BearerTokens? = {
            val session = sessionManager.session()
            BearerTokens(accessToken = session.accessToken, refreshToken = session.refreshToken)
        }

        val expectedCause = Exception("Refresh token failed")
        var isThrowing = false
        val refreshToken: suspend RefreshTokensParams.() -> BearerTokens? = {
            isThrowing = true
            throw expectedCause
        }

        val bearerAuthProvider = BearerAuthProvider(refreshToken, loadToken, { true }, null)

        val mockEngine = MockEngine {
            respondError(status = HttpStatusCode.Unauthorized)
        }

        val client = AuthenticatedNetworkClient(
            mockEngine,
            sessionManager.serverConfig(),
            bearerAuthProvider,
            kaliumLogger,
            false
        )
        val assetApi = AssetApiV0(client)
        val kaliumFileSystem: FileSystem = FakeFileSystem()
        val tempPath = "some-dummy-path".toPath()
        val tempOutputSink = kaliumFileSystem.sink(tempPath)

        val result = assetApi.downloadAsset("asset_id", "asset_domain", null, tempFileSink = tempOutputSink)
        assertIs<NetworkResponse.Error>(result)
        val exception = result.kException
        assertIs<KaliumException.GenericError>(exception)
        assertEquals(expectedCause.message, exception.cause.message)
        assertTrue(isThrowing)
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
        ): SessionDTO = testCredentials.copy(accessToken = UPDATED_ACCESS_TOKEN)

        override fun proxyCredentials(): ProxyCredentialsDTO? = ProxyCredentialsDTO("username", "password")
    }
}
