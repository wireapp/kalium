package com.wire.kalium.network.api.v0.unauthenticated.networkContainer

import com.wire.kalium.network.ServerMetaDataManager
import com.wire.kalium.network.api.base.unAuthenticated.LoginApi
import com.wire.kalium.network.api.base.unAuthenticated.SSOLogin
import com.wire.kalium.network.api.base.unAuthenticated.register.RegisterApi
import com.wire.kalium.network.api.v0.unauthenticated.LoginApiV0
import com.wire.kalium.network.api.v0.unauthenticated.RegisterApiV0
import com.wire.kalium.network.api.v0.unauthenticated.SSOLoginV0
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProviderImpl
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

class UnauthenticatedNetworkContainerV0 internal constructor(
    backendLinks: ServerConfigDTO.Links,
    serverMetaDataManager: ServerMetaDataManager,
    developmentApiEnabled: Boolean = false,
    engine: HttpClientEngine = defaultHttpEngine(),
) : UnauthenticatedNetworkContainer,
    UnauthenticatedNetworkClientProvider by UnauthenticatedNetworkClientProviderImpl(
        backendLinks,
        serverMetaDataManager,
        developmentApiEnabled,
        engine
    ) {
    override val loginApi: LoginApi get() = LoginApiV0(unauthenticatedNetworkClient)
    override val registerApi: RegisterApi get() = RegisterApiV0(unauthenticatedNetworkClient)
    override val sso: SSOLogin get() = SSOLoginV0(unauthenticatedNetworkClient)
}
