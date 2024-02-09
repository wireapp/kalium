/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.serialization.SerializationException

interface ServerConfigApi {
    suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<ServerConfigDTO.Links>
}

class ServerConfigApiImpl internal constructor(
    private val httpClient: HttpClient
) : ServerConfigApi {

    internal constructor(unauthenticatedNetworkClient: UnauthenticatedNetworkClient) : this(
        httpClient = unauthenticatedNetworkClient.httpClient
    )

    internal constructor(unboundNetworkClient: UnboundNetworkClient) : this(
        httpClient = unboundNetworkClient.httpClient
    )

    /**
     * Fetch remote configuration
     * @param serverConfigUrl the remote config url
     */
    override suspend fun fetchServerConfig(serverConfigUrl: String): NetworkResponse<ServerConfigDTO.Links> =
        wrapKaliumResponse<String> {
            httpClient.get {
                accept(ContentType.Text.Plain)
                setUrl(Url(serverConfigUrl))
            }
        }.flatMap {
            try {
                NetworkResponse.Success(
                    KtxSerializer.json.decodeFromString<ServerConfigResponse>(it.value),
                    it.headers,
                    it.httpCode
                )
            } catch (e: SerializationException) {
                NetworkResponse.Error(KaliumException.GenericError(e))
            }
        }.mapSuccess {
            with(it) {
                ServerConfigDTO.Links(
                    api = endpoints.apiBaseUrl,
                    accounts = endpoints.accountsBaseUrl,
                    webSocket = endpoints.webSocketBaseUrl,
                    blackList = endpoints.blackListUrl,
                    website = endpoints.websiteUrl,
                    teams = endpoints.teamsUrl,
                    title = title,
                    isOnPremises = true,
                    apiProxy = apiProxy?.let { proxy ->
                        ServerConfigDTO.ApiProxy(proxy.needsAuthentication, proxy.host, proxy.port)
                    }
                )
            }
        }
}
