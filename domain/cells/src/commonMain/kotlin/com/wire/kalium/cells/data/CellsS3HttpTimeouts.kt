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

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig

internal fun HttpClientConfig<*>.installCellsS3HttpTimeout() {
    install(HttpTimeout) {
        // Preserve the previous AWS SDK behavior: streamed transfers have no total request deadline.
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        connectTimeoutMillis = S3_CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = S3_SOCKET_TIMEOUT_MILLIS
    }
}

// Time allowed to establish the connection, matching the previous AWS SDK configuration.
internal const val S3_CONNECT_TIMEOUT_MILLIS = 2_000L

// Maximum inactivity between packets, matching the previous AWS SDK read/write timeouts.
internal const val S3_SOCKET_TIMEOUT_MILLIS = 30_000L
