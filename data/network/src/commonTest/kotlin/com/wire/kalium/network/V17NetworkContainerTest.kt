/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.network

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.TestSessionManagerV0
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class V17NetworkContainerTest {
    private val config = TEST_BACKEND_CONFIG.copy(
        metaData = TEST_BACKEND_CONFIG.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(17))
    )

    @Test
    fun authenticatedFactoryRetainsMeetingRequestsAndAuthentication() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v17/meetings/list", request.url.encodedPath)
            assertEquals("Bearer ${TestSessionManagerV0.SESSION.accessToken}", request.headers[HttpHeaders.Authorization])
            respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val container = authenticated(engine)
        try {
            val response = container.meetingApi.fetchMeetings()
            assertTrue(response.isSuccessful())
            assertEquals(emptyList(), response.value)
        } finally {
            container.close()
            engine.close()
            engine.coroutineContext.job.join()
        }
    }

    @Test
    fun unauthenticatedFactoryRetainsDomainLookupRequestsAndSerialization() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v17/custom-backend/by-domain/wire.com", request.url.encodedPath)
            respond(
                """{"config_json_url":"https://wire.com/config","webapp_welcome_url":"https://wire.com/welcome"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val container = UnauthenticatedNetworkContainer.create(config, null, "test", false, emptyMap(), engine)
        val client = (container as UnauthenticatedNetworkClientProvider).unauthenticatedNetworkClient.httpClient
        try {
            val response = container.domainLookupApi.lookup("wire.com")
            assertTrue(response.isSuccessful())
            assertEquals("https://wire.com/config", response.value.configJsonUrl)
            assertEquals("https://wire.com/welcome", response.value.webappWelcomeUrl)
        } finally {
            client.close()
            engine.close()
            engine.coroutineContext.job.join()
        }
    }

    @Test
    fun closeDrainsBothAuthenticatedClientsAndTheirOwnedEngine() = runTest {
        val container = authenticated()
        val main = container.cellsHttpClient
        val uncompressed = (container as AuthenticatedHttpClientProvider).networkClientWithoutCompression.httpClient
        try {
            assertNotSame(main, uncompressed)
            assertSame(main.engine, uncompressed.engine)
            assertSame(container.notificationApi, container.notificationApi)
        } finally {
            container.close()
        }
        assertTrue(main.coroutineContext.job.isCompleted)
        assertTrue(uncompressed.coroutineContext.job.isCompleted)
        assertTrue(main.engine.coroutineContext.job.isCompleted)
        container.close()
    }

    private fun authenticated(engine: HttpClientEngine? = null): AuthenticatedNetworkContainer {
        val manager = object : SessionManager by TestSessionManagerV0() {
            override fun serverConfig() = config
            override fun proxyCredentials() = null
        }
        return AuthenticatedNetworkContainer.create(
            manager,
            selfUserId = UserId("self", "wire.com"),
            userAgent = "test",
            certificatePinning = emptyMap(),
            mockEngine = engine,
            mockWebSocketSession = null,
            kaliumLogger = kaliumLogger,
        )
    }
}
