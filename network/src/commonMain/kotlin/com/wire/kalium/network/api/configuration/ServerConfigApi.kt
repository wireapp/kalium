package com.wire.kalium.network.api.configuration

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.http.Url

interface ServerConfigApi {
    suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<ServerConfigDTO>
}

class ServerConfigApiImpl internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : ServerConfigApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    /**
     * Fetch remote configuration
     * @param serverConfigUrl the remote config url
     */
    // TODO: use wrapApiRequest once PR #226 is merged
    override suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<ServerConfigDTO> =
        wrapKaliumResponse<ServerConfigResponse> {
            httpClient.get {
                setUrl(Url(serverConfigUrl))
            }
        }.mapSuccess {
            ServerConfigDTO(
                apiBaseUrl = Url(it.endpoints.apiBaseUrl),
                accountsBaseUrl = Url(it.endpoints.accountsBaseUrl),
                webSocketBaseUrl = Url(it.endpoints.webSocketBaseUrl),
                blackListUrl = Url(it.endpoints.blackListUrl),
                teamsUrl = Url(it.endpoints.teamsUrl),
                websiteUrl = Url(it.endpoints.websiteUrl),
                title = it.title
            )
        }
}
