/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v9.unauthenticated.networkContainer

import com.wire.kalium.network.api.base.unauthenticated.appVersioning.AppVersioningApi
import com.wire.kalium.network.api.base.unauthenticated.appVersioning.AppVersioningApiImpl
import com.wire.kalium.network.api.base.unauthenticated.domainLookup.DomainLookupApi
import com.wire.kalium.network.api.base.unauthenticated.domainregistration.GetDomainRegistrationApi
import com.wire.kalium.network.api.base.unauthenticated.login.LoginApi
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.base.unauthenticated.sso.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.verification.VerificationCodeApi
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApiImpl
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApiImpl
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v9.unauthenticated.DomainLookupApiV9
import com.wire.kalium.network.api.v9.unauthenticated.GetDomainRegistrationApiV9
import com.wire.kalium.network.api.v9.unauthenticated.LoginApiV9
import com.wire.kalium.network.api.v9.unauthenticated.RegisterApiV9
import com.wire.kalium.network.api.v9.unauthenticated.SSOLoginApiV9
import com.wire.kalium.network.api.v9.unauthenticated.VerificationCodeApiV9
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProviderImpl
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import io.ktor.client.engine.HttpClientEngine

@Suppress("LongParameterList")
class UnauthenticatedNetworkContainerV9 internal constructor(
    backendLinks: ServerConfigDTO,
    proxyCredentials: ProxyCredentialsDTO?,
    certificatePinning: CertificatePinning,
    mockEngine: HttpClientEngine?,
    engine: HttpClientEngine = mockEngine ?: defaultHttpEngine(
        serverConfigDTOApiProxy = backendLinks.links.apiProxy,
        proxyCredentials = proxyCredentials,
        certificatePinning = certificatePinning
    ),
    private val developmentApiEnabled: Boolean
) : UnauthenticatedNetworkContainer,
    UnauthenticatedNetworkClientProvider by UnauthenticatedNetworkClientProviderImpl(
        backendLinks,
        engine
    ) {
    override val loginApi: LoginApi get() = LoginApiV9(unauthenticatedNetworkClient)
    override val verificationCodeApi: VerificationCodeApi
        get() = VerificationCodeApiV9(
            unauthenticatedNetworkClient
        )
    override val domainLookupApi: DomainLookupApi
        get() = DomainLookupApiV9(
            unauthenticatedNetworkClient
        )
    override val remoteVersion: VersionApi
        get() = VersionApiImpl(
            unauthenticatedNetworkClient,
            developmentApiEnabled = developmentApiEnabled
        )
    override val serverConfigApi: ServerConfigApi
        get() = ServerConfigApiImpl(unauthenticatedNetworkClient)
    override val registerApi: RegisterApi get() = RegisterApiV9(unauthenticatedNetworkClient)
    override val sso: SSOLoginApi get() = SSOLoginApiV9(unauthenticatedNetworkClient)
    override val appVersioningApi: AppVersioningApi
        get() = AppVersioningApiImpl(
            unauthenticatedNetworkClient
        )
    override val getDomainRegistrationApi: GetDomainRegistrationApi
        get() = GetDomainRegistrationApiV9(unauthenticatedNetworkClient)
}
