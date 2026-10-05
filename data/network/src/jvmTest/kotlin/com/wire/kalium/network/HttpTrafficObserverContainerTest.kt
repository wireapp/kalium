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
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainerCommon
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttpEngine
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpTrafficObserverContainerTest {
    @Test
    fun allVersionsInstallObserverOnlyWhenEnabled() = runTest {
        for (observer in listOf(null, RecordingTrafficObserver())) {
            for (version in 0..18) {
                val config = TEST_BACKEND_CONFIG.copy(metaData = TEST_BACKEND_CONFIG.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(version)))
                val manager = object : SessionManager by TestSessionManagerV0() {
                    override fun serverConfig() = config
                }
                val authenticated = AuthenticatedNetworkContainer.create(
                    manager, selfUserId = UserId("self", "wire.com"), userAgent = "test",
                    certificatePinning = emptyMap(), mockEngine = null, mockWebSocketSession = null,
                    kaliumLogger = kaliumLogger, httpTrafficObserver = observer,
                )
                try {
                    assertInterceptorCount(authenticated.cellsHttpClient, observer)
                } finally {
                    authenticated.close()
                }
                val unauthenticated = UnauthenticatedNetworkContainer.create(config, null, "test", false, emptyMap(), null, observer)
                closeAfterAssertion((unauthenticated as UnauthenticatedNetworkClientProvider).unauthenticatedNetworkClient.httpClient, observer)
            }
            val unbound = UnboundNetworkContainerCommon("test", false, emptyMap(), null, false, observer)
            closeAfterAssertion(unbound.cellsClient, observer)
        }
    }

    private fun assertInterceptorCount(client: HttpClient, observer: HttpTrafficObserver?) {
        val engine = client.engine as OkHttpEngine
        assertEquals(if (observer == null) 0 else 1, engine.config.preconfigured!!.networkInterceptors.size)
    }

    private suspend fun closeAfterAssertion(client: HttpClient, observer: HttpTrafficObserver?) {
        try {
            assertInterceptorCount(client, observer)
        } finally {
            client.close()
            client.engine.close()
            client.engine.coroutineContext.job.join()
        }
    }
}
