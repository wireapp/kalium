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

package com.wire.kalium.logic.data.auth.settings

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.networkContainer.TransientAuthenticatedNetworkContainer
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class PendingLoginSystemSettingsRepositoryTest {
    private val accountTokens = AccountTokens(UserId("user", "domain"), "fresh", "refresh", "Bearer", null)

    @Test
    fun freshBearerTokenIsUsedAndResourcesAreClosed() = runTest {
        val engine = MockEngine { request ->
            assertEquals("Bearer fresh", request.headers[HttpHeaders.Authorization])
            respondJson("""{"ssoIdpChangeDetectionEnabled":true}""")
        }
        val repository = repository(engine)

        assertEquals(Either.Right(true), repository.isIdpChangeDetectionEnabled(accountTokens))
        assertFalse(engine.coroutineContext[Job]!!.isActive)
    }

    @Test
    fun onlyExplicitTrueEnablesDetection() = runTest {
        for (body in listOf(
            """{"ssoIdpChangeDetectionEnabled":false}""",
            """{"ssoIdpChangeDetectionEnabled":null}""",
            "{}",
        )) {
            val engine = MockEngine { respondJson(body) }
            val repository = repository(engine)

            assertEquals(Either.Right(false), repository.isIdpChangeDetectionEnabled(accountTokens))
            assertFalse(engine.coroutineContext[Job]!!.isActive)
        }
    }

    @Test
    fun malformedUnauthorizedServerAndUnsupportedResponsesFailAndCloseResources() = runTest {
        for ((body, status, apiVersion, expectedRequests) in listOf(
            ResponseCase("invalid", HttpStatusCode.OK, 18, 1),
            ResponseCase("{}", HttpStatusCode.Unauthorized, 18, 1),
            ResponseCase("{}", HttpStatusCode.InternalServerError, 18, 1),
            ResponseCase("{}", HttpStatusCode.OK, 3, 0),
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
            val repository = repository(engine, apiVersion)

            assertIs<Either.Left<*>>(repository.isIdpChangeDetectionEnabled(accountTokens))
            assertEquals(expectedRequests, requestCount)
            assertFalse(engine.coroutineContext[Job]!!.isActive)
        }
    }

    @Test
    fun cancellationIsPropagatedAndResourcesAreClosed() = runTest {
        val engine = MockEngine { throw CancellationException("cancelled") }
        val repository = repository(engine)

        assertFailsWith<CancellationException> { repository.isIdpChangeDetectionEnabled(accountTokens) }
        assertFalse(engine.coroutineContext[Job]!!.isActive)
    }

    private fun repository(engine: MockEngine, apiVersion: Int = 18) = PendingLoginSystemSettingsRepositoryImpl(
        containerFactory = { freshSession ->
            val config = newServerConfigDTO(1).let {
                it.copy(metaData = it.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(apiVersion)))
            }
            TransientAuthenticatedNetworkContainer.create(
                session = freshSession,
                serverConfigDTO = config,
                proxyCredentials = null,
                userAgent = "test/useragent",
                certificatePinning = emptyMap(),
                mockEngine = engine,
                kaliumLogger = kaliumLogger,
            )
        }
    )

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private data class ResponseCase(
        val body: String,
        val status: HttpStatusCode,
        val apiVersion: Int,
        val expectedRequests: Int,
    )
}
