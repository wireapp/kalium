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

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.unauthenticated.DomainLookupApi
import com.wire.kalium.network.api.base.unauthenticated.LoginApi
import com.wire.kalium.network.api.base.unauthenticated.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.VerificationCodeApi
import com.wire.kalium.network.api.base.unauthenticated.appVersioning.AppVersioningApi
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.v0.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV0
import com.wire.kalium.network.api.v2.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV2
import com.wire.kalium.network.api.v3.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV3
import com.wire.kalium.network.api.v4.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV4
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

@Suppress("MagicNumber")
interface UnauthenticatedNetworkContainer {
    val loginApi: LoginApi
    val registerApi: RegisterApi
    val sso: SSOLoginApi
    val appVersioningApi: AppVersioningApi
    val verificationCodeApi: VerificationCodeApi
    val domainLookupApi: DomainLookupApi

    companion object {
        fun create(
            serverConfigDTO: ServerConfigDTO,
            proxyCredentials: ProxyCredentialsDTO?,
            userAgent: String,
            certificatePinning: CertificatePinning
        ): UnauthenticatedNetworkContainer {

            KaliumUserAgentProvider.setUserAgent(userAgent)

            return when (serverConfigDTO.metaData.commonApiVersion.version) {
                0 -> UnauthenticatedNetworkContainerV0(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                )

                1 -> UnauthenticatedNetworkContainerV0(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                )

                2 -> UnauthenticatedNetworkContainerV2(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                )

                3 -> UnauthenticatedNetworkContainerV3(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                )

                4 -> UnauthenticatedNetworkContainerV4(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials,
                    certificatePinning = certificatePinning,
                )

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
