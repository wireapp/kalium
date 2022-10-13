package com.wire.kalium.network.api.v2.unauthenticated.networkContainer

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.unauthenticated.LoginApi
import com.wire.kalium.network.api.base.unauthenticated.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.v2.unauthenticated.LoginApiV2
import com.wire.kalium.network.api.v2.unauthenticated.RegisterApiV2
import com.wire.kalium.network.api.v2.unauthenticated.SSOLoginApiV2
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProviderImpl
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

class UnauthenticatedNetworkContainerV2 constructor(
    backendLinks: ServerConfigDTO,
    engine: HttpClientEngine = defaultHttpEngine(),
    proxyCredentials: (() -> ProxyCredentialsDTO?)?
) : UnauthenticatedNetworkContainer,
    UnauthenticatedNetworkClientProvider by UnauthenticatedNetworkClientProviderImpl(
        backendLinks,
        engine,
        proxyCredentials
    ) {
    override val loginApi: LoginApi get() = LoginApiV2(unauthenticatedNetworkClient)
    override val registerApi: RegisterApi get() = RegisterApiV2(unauthenticatedNetworkClient)
    override val sso: SSOLoginApi get() = SSOLoginApiV2(unauthenticatedNetworkClient)
}
