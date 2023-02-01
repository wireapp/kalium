/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.unbound.configuration

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
                api = it.endpoints.apiBaseUrl,
                accounts = it.endpoints.accountsBaseUrl,
                webSocket = it.endpoints.webSocketBaseUrl,
                blackList = it.endpoints.blackListUrl,
                website = it.endpoints.websiteUrl,
                teams = it.endpoints.teamsUrl,
                title = it.title,
                isOnPremises = true,
                apiProxy = it.apiProxy?.let { proxy ->
                    ServerConfigDTO.ApiProxy(proxy.needsAuthentication, proxy.host, proxy.port)
                }
            )
        }
}
