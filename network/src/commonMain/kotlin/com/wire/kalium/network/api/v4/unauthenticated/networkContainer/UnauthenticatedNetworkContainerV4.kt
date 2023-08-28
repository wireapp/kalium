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

package com.wire.kalium.network.api.v4.unauthenticated.networkContainer

import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.unauthenticated.DomainLookupApi
import com.wire.kalium.network.api.base.unauthenticated.LoginApi
import com.wire.kalium.network.api.base.unauthenticated.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.VerificationCodeApi
import com.wire.kalium.network.api.base.unauthenticated.appVersioning.AppVersioningApi
import com.wire.kalium.network.api.base.unauthenticated.appVersioning.AppVersioningApiImpl
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.v4.unauthenticated.DomainLookupApiV4
import com.wire.kalium.network.api.v4.unauthenticated.LoginApiV4
import com.wire.kalium.network.api.v4.unauthenticated.RegisterApiV4
import com.wire.kalium.network.api.v4.unauthenticated.SSOLoginApiV4
import com.wire.kalium.network.api.v4.unauthenticated.VerificationCodeApiV4
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProviderImpl
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

class UnauthenticatedNetworkContainerV4 internal constructor(
    networkStateObserver: NetworkStateObserver,
    backendLinks: ServerConfigDTO,
    proxyCredentials: ProxyCredentialsDTO?,
    certificatePinning: CertificatePinning,
    engine: HttpClientEngine = defaultHttpEngine(
        serverConfigDTOApiProxy = backendLinks.links.apiProxy,
        proxyCredentials = proxyCredentials,
        certificatePinning = certificatePinning
    ),
) : UnauthenticatedNetworkContainer,
    UnauthenticatedNetworkClientProvider by UnauthenticatedNetworkClientProviderImpl(
        networkStateObserver,
        backendLinks,
        engine
    ) {
    override val loginApi: LoginApi get() = LoginApiV4(unauthenticatedNetworkClient)
    override val verificationCodeApi: VerificationCodeApi get() = VerificationCodeApiV4(unauthenticatedNetworkClient)
    override val domainLookupApi: DomainLookupApi get() = DomainLookupApiV4(unauthenticatedNetworkClient)
    override val registerApi: RegisterApi get() = RegisterApiV4(unauthenticatedNetworkClient)
    override val sso: SSOLoginApi get() = SSOLoginApiV4(unauthenticatedNetworkClient)
    override val appVersioningApi: AppVersioningApi get() = AppVersioningApiImpl(unauthenticatedNetworkClient)
}
