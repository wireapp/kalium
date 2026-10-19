/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.network

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.TestSessionManagerV0
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SFTRequestAuthenticationTest {

    @Test
    fun givenArbitrarySftHosts_whenConnectingOnEveryApiVersion_thenNoWireCredentialsAreSent() = runTest {
        val sftUrls = listOf(
            "https://sft.example.test/join",
            "https://sft01.attacker.test:8443/sft/call?id=1",
            "http://plain.example.test/join",
        )
        for (version in SupportedApiVersions) {
            for (sftUrl in sftUrls) {
                val requests = mutableListOf<HttpRequestData>()
                val engine = MockEngine { request ->
                    requests += request
                    respond(byteArrayOf(), HttpStatusCode.OK)
                }
                withAuthenticated(engine, version) { container ->
                    assertTrue(container.sftApi.connectToSFT(url = sftUrl, data = "{}").isSuccessful())
                }
                val request = requests.single()
                assertEquals(Url(sftUrl).host, request.url.host, "v$version $sftUrl")
                assertNull(request.headers[HttpHeaders.Authorization], "v$version $sftUrl")
            }
        }
    }

    @Test
    fun givenSftAndBackendRequests_whenSentOnEveryApiVersion_thenOnlyTheBackendIsAuthenticated() = runTest {
        for (version in SupportedApiVersions) {
            val requests = mutableListOf<HttpRequestData>()
            val engine = MockEngine { request ->
                requests += request
                respond("{}", HttpStatusCode.OK, JSON_HEADERS)
            }
            withAuthenticated(engine, version) { container ->
                assertTrue(container.callApi.getCallConfig(limit = null).isSuccessful())
                assertTrue(container.sftApi.connectToSFT(url = "https://sft.example.test/join", data = "{}").isSuccessful())
                assertTrue(container.callApi.getCallConfig(limit = null).isSuccessful())
            }
            val (configBefore, sft, configAfter) = requests
            assertEquals(BACKEND_HOST, configBefore.url.host)
            assertEquals(BEARER, configBefore.headers[HttpHeaders.Authorization], "v$version")
            assertEquals("sft.example.test", sft.url.host)
            assertNull(sft.headers[HttpHeaders.Authorization], "v$version")
            assertEquals(BEARER, configAfter.headers[HttpHeaders.Authorization], "v$version")
        }
    }

    @Test
    fun givenSftRedirectsToAnotherHost_whenConnecting_thenNoRequestCarriesWireCredentials() = runTest {
        for (status in listOf(HttpStatusCode.TemporaryRedirect, HttpStatusCode.PermanentRedirect, HttpStatusCode.Found)) {
            val requests = mutableListOf<HttpRequestData>()
            val engine = MockEngine { request ->
                requests += request
                if (request.url.host == "sft.example.test") {
                    respond("", status, headersOf(HttpHeaders.Location, "https://redirected.example.test/join"))
                } else {
                    respond(byteArrayOf(), HttpStatusCode.OK)
                }
            }
            withAuthenticated(engine) { container ->
                container.sftApi.connectToSFT(url = "https://sft.example.test/join", data = "{}")
            }
            assertTrue(requests.isNotEmpty())
            requests.forEach { assertNull(it.headers[HttpHeaders.Authorization], "$status -> ${it.url}") }
        }
    }

    @Test
    fun givenSftRespondsWithBearerChallenge_whenConnecting_thenTheWireTokenIsNeitherRefreshedNorSent() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
        }
        withAuthenticated(engine) { container ->
            assertFalse(container.sftApi.connectToSFT(url = "https://sft.example.test/join", data = "{}").isSuccessful())
        }
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun givenAContainer_whenClosed_thenTheSeparateSftClientIsClosedToo() = runTest {
        val engine = MockEngine { fail("No network request expected") }
        withAuthenticated(engine) { container ->
            val provider = container as AuthenticatedHttpClientProvider
            val sftClient = provider.sftNetworkClient.httpClient
            assertNotSame(provider.networkClient.httpClient, sftClient)
            container.close()
            assertTrue(sftClient.coroutineContext.job.isCompleted)
        }
    }

    private suspend fun withAuthenticated(
        engine: MockEngine,
        version: Int = SupportedApiVersions.max(),
        block: suspend (AuthenticatedNetworkContainer) -> Unit,
    ) {
        val manager = object : SessionManager by TestSessionManagerV0() {
            override fun serverConfig() = TEST_BACKEND_CONFIG.copy(
                metaData = TEST_BACKEND_CONFIG.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(version))
            )

            override fun proxyCredentials() = null
            override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String?): SessionDTO =
                fail("SFT responses must never trigger a Wire token refresh")
        }
        val container = AuthenticatedNetworkContainer.create(
            manager,
            selfUserId = UserId("self", "wire.com"),
            userAgent = "test",
            certificatePinning = emptyMap(),
            mockEngine = engine,
            mockWebSocketSession = null,
            kaliumLogger = kaliumLogger,
        )
        try {
            block(container)
        } finally {
            container.close()
            engine.close()
            engine.coroutineContext.job.join()
        }
    }

    private companion object {
        val BACKEND_HOST = Url(TEST_BACKEND_CONFIG.links.api).host
        val BEARER = "Bearer ${TestSessionManagerV0.SESSION.accessToken}"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
