package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.unauthenticated.LoginApi
import com.wire.kalium.network.api.base.unauthenticated.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.v0.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV0
import com.wire.kalium.network.api.v2.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV2
import com.wire.kalium.network.api.v3.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV3
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

@Suppress("MagicNumber")
interface UnauthenticatedNetworkContainer {
    val loginApi: LoginApi
    val registerApi: RegisterApi
    val sso: SSOLoginApi

    companion object {
        fun create(
            serverConfigDTO: ServerConfigDTO,
            proxyCredentials: ProxyCredentialsDTO?
        ): UnauthenticatedNetworkContainer {
            return when (serverConfigDTO.metaData.commonApiVersion.version) {
                0 -> UnauthenticatedNetworkContainerV0(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials
                )

                1 -> UnauthenticatedNetworkContainerV0(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials
                )

                2 -> UnauthenticatedNetworkContainerV2(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials
                )

                3 -> UnauthenticatedNetworkContainerV3(
                    serverConfigDTO,
                    proxyCredentials = proxyCredentials
                )

                else -> throw error("Unsupported version: ${serverConfigDTO.metaData.commonApiVersion.version}")
            }
        }
    }
}

internal interface UnauthenticatedNetworkClientProvider {
    val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
}

internal class UnauthenticatedNetworkClientProviderImpl internal constructor(
    backendLinks: ServerConfigDTO,
    proxyCredentials: ProxyCredentialsDTO?,
    engine: HttpClientEngine = defaultHttpEngine(backendLinks.links.apiProxy, proxyCredentials),
) : UnauthenticatedNetworkClientProvider {
    override val unauthenticatedNetworkClient by lazy {
        UnauthenticatedNetworkClient(engine, backendLinks)
    }
}
