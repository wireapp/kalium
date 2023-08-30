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

import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.KaliumUserAgentProvider
import com.wire.kalium.network.networkContainer.UnboundNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import io.ktor.client.engine.HttpClientEngine

internal class FakeUnboundNetworkClientProviderImpl(
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

class FakeUnboundNetworkContainer(
    networkStateObserver: NetworkStateObserver,
    private val developmentApiEnabled: Boolean,
    userAgent: String,
    private val ignoreSSLCertificates: Boolean,
    certificatePinning: CertificatePinning
) : UnboundNetworkContainer,
    UnboundNetworkClientProvider by FakeUnboundNetworkClientProviderImpl(
        networkStateObserver = networkStateObserver,
        userAgent = userAgent,
        engine = defaultHttpEngine(
            ignoreSSLCertificates = ignoreSSLCertificates,
            certificatePinning = certificatePinning
        )
    ) {
    override val serverConfigApi: ServerConfigApi get() = FakeServerConfigApiImpl(unboundNetworkClient)
    override val remoteVersion: VersionApi get() = FakeVersionApiImpl(unboundNetworkClient, developmentApiEnabled)
    override val acmeApi: ACMEApi get() = FakeACMEApiImpl(unboundNetworkClient)
}
