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

package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApiImpl
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.session.CertificatePinning
import io.ktor.client.engine.HttpClientEngine

interface UnboundNetworkContainer {
    val serverConfigApi: ServerConfigApi
    val acmeApi: ACMEApi
}

private interface UnboundNetworkClientProvider {
    val unboundNetworkClient: UnboundNetworkClient
}

internal class UnboundNetworkClientProviderImpl(
    networkStateObserver: NetworkStateObserver,
    userAgent: String,
    engine: HttpClientEngine
) : UnboundNetworkClientProvider {

    init {
        KaliumUserAgentProvider.setUserAgent(userAgent)
    }

    override val unboundNetworkClient by lazy {
        UnboundNetworkClient(networkStateObserver, engine)
    }
}

class UnboundNetworkContainerCommon(
    networkStateObserver: NetworkStateObserver,
    userAgent: String,
    certificatePinning: CertificatePinning,
    mockEngine: HttpClientEngine?
) : UnboundNetworkContainer,
    UnboundNetworkClientProvider by UnboundNetworkClientProviderImpl(
        networkStateObserver,
        userAgent,
        engine = mockEngine ?: defaultHttpEngine(
            certificatePinning = certificatePinning,
            proxyCredentials = null,
            serverConfigDTOApiProxy = null
        )
    ) {
    override val serverConfigApi: ServerConfigApi get() = ServerConfigApiImpl(unboundNetworkClient)
    override val acmeApi: ACMEApi get() = ACMEApiImpl(unboundNetworkClient)
}
