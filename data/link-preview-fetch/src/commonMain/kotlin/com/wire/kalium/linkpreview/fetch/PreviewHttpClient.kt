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
package com.wire.kalium.linkpreview.fetch

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent

/** A separate transport enforces the public-address policy at connection time on each platform. */
internal expect fun createPreviewHttpEngine(): HttpClientEngine

internal fun createPreviewHttpClient(): HttpClient = HttpClient(PreviewHttpEngineFactory) {
    install(HttpTimeout) {
        requestTimeoutMillis = PREVIEW_REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = PREVIEW_CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = PREVIEW_REQUEST_TIMEOUT_MILLIS
    }
    install(UserAgent) {
        agent = "Wire LinkPreviewFetcher"
    }
    followRedirects = false
    expectSuccess = false
}

private object PreviewHttpEngineFactory : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
        createPreviewHttpEngine().also { it.config.apply(block) }
}

private const val PREVIEW_REQUEST_TIMEOUT_MILLIS = 5_000L
private const val PREVIEW_CONNECT_TIMEOUT_MILLIS = 3_000L
