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

package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.networkContainer.TransientAuthenticatedNetworkContainer
import io.ktor.client.engine.mock.MockEngine
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

class FetchPendingLoginSystemSettingsTest {
    private val session = SessionDTO(QualifiedID("user", "domain"), "Bearer", "fresh", "refresh", null)

    @Test
    fun capabilityIsReturnedAndResourcesAreClosed() = runTest {
        val engine = MockEngine {
            respond(
                """{"ssoIdpChangeDetectionEnabled":true}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val fetch = operation(engine)

        assertEquals(Either.Right(true), fetch(session))
        assertFalse(engine.coroutineContext[Job]!!.isActive)
    }

    @Test
    fun networkFailureIsReturnedAndResourcesAreClosed() = runTest {
        val engine = MockEngine {
            respond(
                "{}",
                HttpStatusCode.InternalServerError,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val fetch = operation(engine)

        assertIs<Either.Left<*>>(fetch(session))
        assertFalse(engine.coroutineContext[Job]!!.isActive)
    }

    @Test
    fun cancellationIsPropagatedAndResourcesAreClosed() = runTest {
        val engine = MockEngine { throw CancellationException("cancelled") }
        val fetch = operation(engine)

        assertFailsWith<CancellationException> { fetch(session) }
        assertFalse(engine.coroutineContext[Job]!!.isActive)
    }

    private fun operation(engine: MockEngine) = FetchPendingLoginSystemSettingsImpl { freshSession ->
        val config = newServerConfigDTO(1).let {
            it.copy(metaData = it.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(18)))
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
}
