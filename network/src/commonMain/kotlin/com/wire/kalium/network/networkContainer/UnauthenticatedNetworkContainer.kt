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

package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.unauthenticated.appVersioning.AppVersioningApi
import com.wire.kalium.network.api.base.unauthenticated.domainLookup.DomainLookupApi
import com.wire.kalium.network.api.base.unauthenticated.login.LoginApi
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.base.unauthenticated.sso.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.verification.VerificationCodeApi
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v0.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV0
import com.wire.kalium.network.api.v2.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV2
import com.wire.kalium.network.api.v4.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV4
import com.wire.kalium.network.api.v5.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV5
import com.wire.kalium.network.api.v6.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV6
import com.wire.kalium.network.session.CertificatePinning
import io.ktor.client.engine.HttpClientEngine

@Suppress("MagicNumber", "LongParameterList")
interface UnauthenticatedNetworkContainer {
    val loginApi: LoginApi
    val registerApi: RegisterApi
    val sso: SSOLoginApi
    val appVersioningApi: AppVersioningApi
    val verificationCodeApi: VerificationCodeApi
    val domainLookupApi: DomainLookupApi
    val remoteVersion: VersionApi
    val serverConfigApi: ServerConfigApi

    @Suppress("LongMethod")
    companion object {
        fun create(
            serverConfigDTO: ServerConfigDTO,
            proxyCredentials: ProxyCredentialsDTO?,
            userAgent: String,
            developmentApiEnabled: Boolean,
            certificatePinning: CertificatePinning,
            mockEngine: HttpClientEngine?
        ): UnauthenticatedNetworkContainer {

            KaliumUserAgentProvider.setUserAgent(userAgent)

            return when (serverConfigDTO.metaData.commonApiVersion.version) {
                0 -> UnauthenticatedNetworkContainerV0(
                    developmentApiEnabled = developmentApiEnabled,
                    backendLinks = serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                    mockEngine
                )

                1 -> UnauthenticatedNetworkContainerV0(
                    developmentApiEnabled,
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                    mockEngine
                )

                2 -> UnauthenticatedNetworkContainerV2(
                    developmentApiEnabled = developmentApiEnabled,
                    backendLinks = serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                    mockEngine = mockEngine
                )

                // this is intentional since we should drop support for api v3
                // and we default back to v2
                3 -> UnauthenticatedNetworkContainerV2(
                    developmentApiEnabled = developmentApiEnabled,
                    backendLinks = serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                    mockEngine = mockEngine,
                )

                4 -> UnauthenticatedNetworkContainerV4(
                    developmentApiEnabled = developmentApiEnabled,
                    backendLinks = serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                    mockEngine = mockEngine,
                )

                5 -> UnauthenticatedNetworkContainerV5(
                    developmentApiEnabled = developmentApiEnabled,
                    backendLinks = serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                    mockEngine = mockEngine,
                )

                6 -> UnauthenticatedNetworkContainerV6(
                    backendLinks = serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                    mockEngine = mockEngine,
                    developmentApiEnabled = developmentApiEnabled
                )

                // You can use scripts/generate_new_api_version.sh or gradle task network:generateNewApiVersion to
                // bump API version and generate all needed classes
                else -> error("Unsupported version: ${serverConfigDTO.metaData.commonApiVersion.version}")
            }
        }
    }
}

internal interface UnauthenticatedNetworkClientProvider {
    val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
}

internal class UnauthenticatedNetworkClientProviderImpl internal constructor(
    backendLinks: ServerConfigDTO,
    engine: HttpClientEngine
) : UnauthenticatedNetworkClientProvider {
    override val unauthenticatedNetworkClient by lazy {
        UnauthenticatedNetworkClient(engine, backendLinks)
    }
}
