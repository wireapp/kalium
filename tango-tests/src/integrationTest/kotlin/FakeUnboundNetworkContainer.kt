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
import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.base.unauthenticated.DomainLookupApi
import com.wire.kalium.network.api.base.unauthenticated.LoginApi
import com.wire.kalium.network.api.base.unauthenticated.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.VerificationCodeApi
import com.wire.kalium.network.api.base.unauthenticated.appVersioning.AppVersioningApi
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApiImpl
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApiImpl
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.KaliumUserAgentProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel

internal class FakeUnboundNetworkClientProviderImpl(
    networkStateObserver: NetworkStateObserver,
    userAgent: String,
    engine: HttpClientEngine,
    networkClient: UnboundNetworkClient
) : UnboundNetworkClientProvider {

    init {
        KaliumUserAgentProvider.setUserAgent(userAgent)
    }

    override val unboundNetworkClient = networkClient
//     override val unboundNetworkClient by lazy {
//         // UnboundNetworkClient(networkStateObserver, engine)
//         networkClient
//     }
}

internal class FakeUnauthenticatedUnboundNetworkClientProviderImpl(
    networkStateObserver: NetworkStateObserver,
    userAgent: String,
    engine: HttpClientEngine,
    networkClient: UnauthenticatedNetworkClient
) : UnauthenticatedNetworkClientProvider { // } UnboundNetworkClientProvider {

    init {
        KaliumUserAgentProvider.setUserAgent(userAgent)
    }

    override val unauthenticatedNetworkClient  = networkClient

    // override val unboundNetworkClient = networkClient
//     override val unboundNetworkClient by lazy {
//         // UnboundNetworkClient(networkStateObserver, engine)
//         networkClient
//     }
}

class FakeUnboundNetworkContainer(
    networkStateObserver: NetworkStateObserver,
    private val developmentApiEnabled: Boolean,
    userAgent: String,
    private val ignoreSSLCertificates: Boolean,
    certificatePinning: CertificatePinning,
    networkClient: UnboundNetworkClient
) : UnboundNetworkContainer,
    UnboundNetworkClientProvider by FakeUnboundNetworkClientProviderImpl(
        networkStateObserver = networkStateObserver,
        userAgent = userAgent,
        engine = defaultHttpEngine(
            ignoreSSLCertificates = ignoreSSLCertificates,
            certificatePinning = certificatePinning
        ),
        networkClient = networkClient
    ) {
    override val serverConfigApi: ServerConfigApi get() = ServerConfigApiImpl(unboundNetworkClient)
    override val remoteVersion: VersionApi get() = VersionApiImpl(unboundNetworkClient, developmentApiEnabled)
    override val acmeApi: ACMEApi get() = ACMEApiImpl(unboundNetworkClient)
}

class FakeUnauthenticatedUnboundNetworkContainer(
    networkStateObserver: NetworkStateObserver,
    private val developmentApiEnabled: Boolean,
    userAgent: String,
    private val ignoreSSLCertificates: Boolean,
    certificatePinning: CertificatePinning,
    networkClient: UnauthenticatedNetworkClient
) : UnauthenticatedNetworkClientProvider by FakeUnauthenticatedUnboundNetworkClientProviderImpl(
        networkStateObserver = networkStateObserver,
        userAgent = userAgent,
        engine = defaultHttpEngine(
            ignoreSSLCertificates = ignoreSSLCertificates,
            certificatePinning = certificatePinning
        ),
        networkClient = networkClient
    ) {
//     override val loginApi: LoginApi
//         get() = TODO("Not yet implemented")
//     override val registerApi: RegisterApi
//         get() = TODO("Not yet implemented")
//     override val sso: SSOLoginApi
//         get() = TODO("Not yet implemented")
//     override val appVersioningApi: AppVersioningApi
//         get() = TODO("Not yet implemented")
//     override val verificationCodeApi: VerificationCodeApi
//         get() = TODO("Not yet implemented")
//     override val domainLookupApi: DomainLookupApi
//         get() = TODO("Not yet implemented")

    // override val serverConfigApi: ServerConfigApi get() = ServerConfigApiImpl(unauthenticatedNetworkClient)
    // override val remoteVersion: VersionApi get() = VersionApiImpl(unboundNetworkClient, developmentApiEnabled)
    // override val acmeApi: ACMEApi get() = ACMEApiImpl(unboundNetworkClient)
}
