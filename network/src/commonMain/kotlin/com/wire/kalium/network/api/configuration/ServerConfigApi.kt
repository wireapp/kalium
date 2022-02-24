package com.wire.kalium.network.api.configuration

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get

interface ServerConfigApi {
    suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<NetworkConfigDTO>
}

class ServerConfigApiImp(private val httpClient: HttpClient) : ServerConfigApi {

    /**
     * Fetch remote configuration
     * @param serverConfigUrl the remote config url
     */
    override suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<NetworkConfigDTO> = wrapKaliumResponse {
        httpClient.get(serverConfigUrl)
    }
}
