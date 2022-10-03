package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionResponse
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.base.authenticated.connection.UpdateConnectionRequest
import com.wire.kalium.network.api.base.model.PaginationRequest
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class ConnectionApiV0 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) :
    ConnectionApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

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

    private companion object {
        const val PATH_CONNECTIONS = "list-connections"
        const val PATH_CONNECTIONS_ENDPOINTS = "connections"
        const val MAX_CONNECTIONS_COUNT = 500
    }
}
