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
package com.wire.kalium.network

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun buildOkhttpClient(
    block: OkHttpClient.Builder.() -> Unit
): OkHttpClient = OkHttpClient.Builder()
    .apply(block)
    .apply {

        // OkHttp doesn't support configuring ping intervals dynamically,
        // so they must be set when creating the Engine
        // See https://youtrack.jetbrains.com/issue/KTOR-4752
        pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .connectTimeout(WEBSOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(WEBSOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(WEBSOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
    }.connectionSpecs(supportedConnectionSpecs()).build()

fun buildClearTextTrafficOkhttpClient(): OkHttpClient = OkHttpClient.Builder().apply {
    connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
}.build()
