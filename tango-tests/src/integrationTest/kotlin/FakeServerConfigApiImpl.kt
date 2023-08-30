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

import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse

class FakeServerConfigApiImpl internal constructor(
    private val unboundNetworkClient: UnboundNetworkClient
) : ServerConfigApi {

    private val httpClient get() = unboundNetworkClient.httpClient

    /**
     * Fetch remote configuration
     * @param serverConfigUrl the remote config url
     */
    override suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<ServerConfigDTO.Links> {
        val apiBaseUrl = "apiBaseUrl"
        val accountsBaseUrl = "accountsBaseUrl"
        val webSocketBaseUrl = "webSocketBaseUrl"
        val blackListUrl = "blackListUrl"
        val websiteUrl = "websiteUrl"
        val teamsUrl = "teamsUrl"
        val title = "title"
        val proxyHost = "fakeHost"
        val proxyPort = 13000

        return NetworkResponse.Success(
            value = ServerConfigDTO.Links(
                api = apiBaseUrl,
                accounts = accountsBaseUrl,
                webSocket = webSocketBaseUrl,
                blackList = blackListUrl,
                website = websiteUrl,
                teams = teamsUrl,
                title = title,
                isOnPremises = true,
                apiProxy = ServerConfigDTO.ApiProxy(false, proxyHost, proxyPort)
            ),
            headers = mapOf(),
            httpCode = 200
        )
    }
}
