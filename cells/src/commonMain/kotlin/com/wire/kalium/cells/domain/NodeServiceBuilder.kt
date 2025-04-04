/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain

import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.network.session.installAuth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens

internal object NodeServiceBuilder {

    private const val API_VERSION = "v2"

    private var httpClient: HttpClient? = null
    private var baseUrl: String? = null
    private var accessToken: String? = null

    fun withCredentials(credentials: CellsCredentials): NodeServiceBuilder {
        baseUrl = "${credentials.serverUrl}/$API_VERSION"
        accessToken = credentials.accessToken
        return this
    }

    fun withHttpClient(httpClient: HttpClient): NodeServiceBuilder {
        this.httpClient = httpClient
        return this
    }

    fun build(): NodeServiceApi {
        return NodeServiceApi(
            baseUrl = baseUrl ?: error("Base URL is not set"),
            httpClient = httpClient?.config {
                installAuth(
                    BearerAuthProvider(
                        loadTokens = { BearerTokens(accessToken ?: error("Access token not set"), "") },
                        refreshTokens = { null },
                        realm = null
                    )
                )
            } ?: error("HttpClient is not set")
        )
    }
}
