package com.wire.kalium.network.api.user.connection

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.buildJsonObject

interface ConnectionApi {

    suspend fun fetchSelfUserConnections(pagingState: String?): NetworkResponse<ConnectionResponse>
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

    private companion object {
        const val PATH_CONNECTIONS = "/list-connections"
    }
}
