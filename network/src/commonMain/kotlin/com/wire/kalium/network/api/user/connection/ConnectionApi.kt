package com.wire.kalium.network.api.user.connection

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.json.buildJsonObject

interface ConnectionApi {

    suspend fun fetchSelfUserConnections(pagingState: String?): NetworkResponse<ConnectionResponse>
    suspend fun createConnection(userId: UserId): NetworkResponse<ConnectionDTO>
    suspend fun updateConnection(userId: UserId, connectionStatus: ConnectionStateDTO): NetworkResponse<ConnectionDTO>
}

class ConnectionApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : ConnectionApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun fetchSelfUserConnections(pagingState: String?): NetworkResponse<ConnectionResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_CONNECTIONS) {
                setBody(
                    buildJsonObject {
                        pagingState?.let {
                            "paging_state" to it
                        }
                    }
                )
            }
        }

    override suspend fun createConnection(userId: UserId): NetworkResponse<ConnectionDTO> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONNECTIONS_ENDPOINTS/${userId.domain}/${userId.value}")
        }

    override suspend fun updateConnection(userId: UserId, connectionStatus: ConnectionStateDTO): NetworkResponse<ConnectionDTO> =
        wrapKaliumResponse {
            httpClient.put("$PATH_CONNECTIONS_ENDPOINTS/${userId.domain}/${userId.value}") {
                setBody(connectionStatus)
            }
        }

    private companion object {
        const val PATH_CONNECTIONS = "/list-connections"
        const val PATH_CONNECTIONS_ENDPOINTS = "/connections"
    }
}
