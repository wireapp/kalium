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
package com.wire.kalium.cells.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CellsS3HttpTimeoutsTest {

    @Test
    fun givenCellsS3HttpClient_whenSendingRequest_thenTransferAndNetworkTimeoutsMatchAwsSdk() = runTest {
        val httpClient = HttpClient(
            MockEngine { request ->
                val timeout = assertNotNull(request.getCapabilityOrNull(HttpTimeoutCapability))
                assertEquals(HttpTimeoutConfig.INFINITE_TIMEOUT_MS, timeout.requestTimeoutMillis)
                assertEquals(S3_CONNECT_TIMEOUT_MILLIS, timeout.connectTimeoutMillis)
                assertEquals(S3_SOCKET_TIMEOUT_MILLIS, timeout.socketTimeoutMillis)
                respondOk()
            }
        ) {
            installCellsS3HttpTimeout()
        }

        httpClient.get("https://cells.example.test")
    }
}
