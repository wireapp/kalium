package com.wire.kalium.network.api.user.connection

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.buildJsonObject

interface ConnectionApi {

    suspend fun fetchSelfUserConnections(pagingState: String?): NetworkResponse<ConnectionResponse>
}

class ConnectionApiImpl(private val httpClient: HttpClient) : ConnectionApi {

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
