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

package com.wire.kalium.api.v4

import com.wire.kalium.api.TestSessionManagerV0
import com.wire.kalium.network.api.authenticated.systemsettings.SystemSettingsResponse
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.networkContainer.TransientAuthenticatedNetworkContainer
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

internal class SystemSettingsApiTest {
    private val sessionManager = TestSessionManagerV0()

    @Test
    fun freshBearerTokenAndSelectedApiVersionAreUsed() = runTest {
        val session = sessionManager.session()
        val config = configForVersion(18)
        val engine = MockEngine { request ->
            assertEquals("/v18/system/settings", request.url.encodedPath)
            assertEquals("Bearer ${session.accessToken}", request.headers[HttpHeaders.Authorization])
            respondJson("""{"ssoIdpChangeDetectionEnabled":true}""")
        }
        val container = container(session, config, engine)

        val result = container.systemSettingsApi.settings()

        assertIs<NetworkResponse.Success<SystemSettingsResponse>>(result)
        assertEquals(true, result.value.ssoIdpChangeDetectionEnabled)
        container.close()
        assertFalse(engine.coroutineContext[Job]!!.isActive)
    }

    @Test
    fun trueFalseNullAndMissingCapabilitiesAreDecoded() = runTest {
        for ((body, expected) in listOf(
            """{"ssoIdpChangeDetectionEnabled":true}""" to true,
            """{"ssoIdpChangeDetectionEnabled":false}""" to false,
            """{"ssoIdpChangeDetectionEnabled":null}""" to null,
            "{}" to null,
        )) {
            val engine = MockEngine { respondJson(body) }
            val container = container(sessionManager.session(), configForVersion(4), engine)

            val result = container.systemSettingsApi.settings()

            assertIs<NetworkResponse.Success<SystemSettingsResponse>>(result)
            assertEquals(expected, result.value.ssoIdpChangeDetectionEnabled)
            container.close()
        }
    }

    @Test
    fun unsupportedVersionDoesNotMakeARequest() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respondJson("{}")
        }
        val container = container(sessionManager.session(), configForVersion(3), engine)

        assertIs<NetworkResponse.Error>(container.systemSettingsApi.settings())
        assertEquals(0, requestCount)
        container.close()
    }

    @Test
    fun malformedUnauthorizedAndServerResponsesFailWithoutRetry() = runTest {
        for ((body, status) in listOf(
            "invalid" to HttpStatusCode.OK,
            "{}" to HttpStatusCode.Unauthorized,
            "{}" to HttpStatusCode.InternalServerError,
        )) {
            var requestCount = 0
            val engine = MockEngine {
                requestCount++
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.WWWAuthenticate to listOf("Bearer"),
                    ),
                )
            }
            val container = container(sessionManager.session(), configForVersion(18), engine)

            assertIs<NetworkResponse.Error>(container.systemSettingsApi.settings())
            assertEquals(1, requestCount)
            container.close()
        }
    }

    private fun configForVersion(version: Int) = sessionManager.serverConfig().let {
        it.copy(metaData = it.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(version)))
    }

    private fun container(
        session: SessionDTO,
        config: ServerConfigDTO,
        engine: MockEngine,
    ) = TransientAuthenticatedNetworkContainer.create(
        session = session,
        serverConfigDTO = config,
        proxyCredentials = null,
        userAgent = "test/useragent",
        certificatePinning = emptyMap(),
        mockEngine = engine,
        kaliumLogger = kaliumLogger,
    )

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
}
