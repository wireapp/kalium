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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.authenticated.connection.ConnectionResponse
import com.wire.kalium.network.api.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.authenticated.connection.UpdateConnectionRequest
import com.wire.kalium.network.api.model.PaginationRequest
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class ConnectionApiV0 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) :
    ConnectionApi {

    protected val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun fetchSelfUserConnections(pagingState: String?): NetworkResponse<ConnectionResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_CONNECTIONS) {
                setBody(PaginationRequest(pagingState = pagingState, size = MAX_CONNECTIONS_COUNT))
            }
        }

    override suspend fun createConnection(userId: UserId): NetworkResponse<ConnectionDTO> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONNECTIONS_ENDPOINTS/${userId.domain}/${userId.value}")
        }

    override suspend fun updateConnection(userId: UserId, connectionStatus: ConnectionStateDTO): NetworkResponse<ConnectionDTO> =
        wrapKaliumResponse {
            httpClient.put("$PATH_CONNECTIONS_ENDPOINTS/${userId.domain}/${userId.value}") {
                setBody(UpdateConnectionRequest(connectionStatus))
            }
        }

    override suspend fun userConnectionInfo(userId: UserId): NetworkResponse<ConnectionDTO> = wrapKaliumResponse {
        httpClient.get("$PATH_CONNECTIONS_ENDPOINTS/${userId.domain}/${userId.value}")
    }

    protected companion object {
        const val PATH_CONNECTIONS = "list-connections"
        const val PATH_CONNECTIONS_ENDPOINTS = "connections"
        const val MAX_CONNECTIONS_COUNT = 500
    }
}
