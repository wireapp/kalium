package com.wire.kalium.network.api.configuration

import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.http.Url

interface ServerConfigApi {
    suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<ServerConfigDTO.Links>
}

class ServerConfigApiImpl internal constructor(
    private val unboundNetworkClient: UnboundNetworkClient
) : ServerConfigApi {

    private val httpClient get() = unboundNetworkClient.httpClient

    /**
     * Fetch remote configuration
     * @param serverConfigUrl the remote config url
     */
    override suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<ServerConfigDTO.Links> =
        wrapKaliumResponse<ServerConfigResponse> {
            httpClient.get {
                setUrl(Url(serverConfigUrl))
            }
        }.mapSuccess {
            ServerConfigDTO.Links(
                api = Url(it.endpoints.apiBaseUrl),
                accounts = Url(it.endpoints.accountsBaseUrl),
                webSocket = Url(it.endpoints.webSocketBaseUrl),
                blackList = Url(it.endpoints.blackListUrl),
                website = Url(it.endpoints.websiteUrl),
                teams = Url(it.endpoints.teamsUrl),
                title = it.title
            )
        }
}
