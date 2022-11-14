package com.wire.kalium.network.api.v3.unauthenticated.networkContainer

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.unauthenticated.LoginApi
import com.wire.kalium.network.api.base.unauthenticated.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.v3.unauthenticated.LoginApiV3
import com.wire.kalium.network.api.v3.unauthenticated.RegisterApiV3
import com.wire.kalium.network.api.v3.unauthenticated.SSOLoginApiV3
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProviderImpl
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

class UnauthenticatedNetworkContainerV3 constructor(
    backendLinks: ServerConfigDTO,
    proxyCredentials: ProxyCredentialsDTO?,
    engine: HttpClientEngine = defaultHttpEngine(backendLinks.links.apiProxy, proxyCredentials),
) : UnauthenticatedNetworkContainer,
    UnauthenticatedNetworkClientProvider by UnauthenticatedNetworkClientProviderImpl(
        backendLinks,
        proxyCredentials,
        engine
    ) {
    override val loginApi: LoginApi get() = LoginApiV3(unauthenticatedNetworkClient)
    override val registerApi: RegisterApi get() = RegisterApiV3(unauthenticatedNetworkClient)
    override val sso: SSOLoginApi get() = SSOLoginApiV3(unauthenticatedNetworkClient)
}
