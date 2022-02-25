package com.wire.kalium.network.api.configuration

import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get

interface ServerConfigApi {
    suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<BackendConfig>
}

class ServerConfigApiImp(private val httpClient: HttpClient) : ServerConfigApi {

    /**
     * Fetch remote configuration
     * @param serverConfigUrl the remote config url
     */
    // TODO: use wrapApiRequest once PR #226 is merged
    override suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<BackendConfig> =
        wrapKaliumResponse<ServerConfigResponse> {
            httpClient.get(serverConfigUrl)
        }.mapSuccess {
            print("xxxxxx ${it.toString()}")
            BackendConfig(
                apiBaseUrl = it.endpoints.apiBaseUrl,
                accountsBaseUrl = it.endpoints.accountsBaseUrl,
                webSocketBaseUrl = it.endpoints.webSocketBaseUrl,
                blackListUrl = it.endpoints.blackListUrl,
                teamsUrl = it.endpoints.teamsUrl,
                websiteUrl = it.endpoints.websiteUrl,
                title = it.title
            )
        }
}
