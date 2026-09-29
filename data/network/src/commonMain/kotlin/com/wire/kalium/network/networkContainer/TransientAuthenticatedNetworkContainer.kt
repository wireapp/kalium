/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.network.networkContainer

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.HttpTrafficObserver
import com.wire.kalium.network.api.base.authenticated.SystemSettingsApi
import com.wire.kalium.network.api.base.authenticated.SystemSettingsApiImpl
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.session.CertificatePinning
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens

/**
 * A short-lived authenticated container for requests made before a user session is stored.
 * It owns its client and engine and never refreshes or persists the supplied credentials.
 */
class TransientAuthenticatedNetworkContainer private constructor(
    val systemSettingsApi: SystemSettingsApi,
    private val networkClient: AuthenticatedNetworkClient,
    private val engine: HttpClientEngine,
) {
    fun close() {
        networkClient.httpClient.close()
        engine.close()
    }

    companion object {
        @Suppress("LongParameterList")
        fun create(
            session: SessionDTO,
            serverConfigDTO: ServerConfigDTO,
            proxyCredentials: ProxyCredentialsDTO?,
            userAgent: String,
            certificatePinning: CertificatePinning,
            mockEngine: HttpClientEngine?,
            kaliumLogger: KaliumLogger,
            httpTrafficObserver: HttpTrafficObserver? = null,
        ): TransientAuthenticatedNetworkContainer {
            KaliumUserAgentProvider.setUserAgent(userAgent)

            val engine = mockEngine ?: defaultHttpEngine(
                serverConfigDTOApiProxy = serverConfigDTO.links.apiProxy,
                proxyCredentials = proxyCredentials,
                certificatePinning = certificatePinning,
                httpTrafficObserver = httpTrafficObserver,
            )
            val bearerAuthProvider = BearerAuthProvider(
                refreshTokens = { null },
                loadTokens = {
                    BearerTokens(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                    )
                },
                sendWithoutRequestCallback = { true },
                realm = null,
            )
            val networkClient = AuthenticatedNetworkClient(
                engine = engine,
                serverConfigDTO = serverConfigDTO,
                bearerAuthProvider = bearerAuthProvider,
                kaliumLogger = kaliumLogger,
            )

            return TransientAuthenticatedNetworkContainer(
                systemSettingsApi = SystemSettingsApiImpl(
                    networkClient = networkClient,
                    apiVersion = serverConfigDTO.metaData.commonApiVersion.version,
                ),
                networkClient = networkClient,
                engine = engine,
            )
        }
    }
}
