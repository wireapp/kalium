package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.ServerMetaDataManager
import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImpl
import com.wire.kalium.network.api.user.login.SSOLoginApi
import com.wire.kalium.network.api.user.login.SSOLoginApiImpl
import com.wire.kalium.network.api.user.register.RegisterApi
import com.wire.kalium.network.api.user.register.RegisterApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

interface UnauthenticatedNetworkContainer {
    val loginApi: LoginApi
    val registerApi: RegisterApi
    val sso: SSOLoginApi
}

private interface UnauthenticatedNetworkClientProvider {
    val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
}

internal class UnauthenticatedNetworkClientProviderImpl(
    backendLinks: ServerConfigDTO.Links,
    serverMetaDataManager: ServerMetaDataManager,
    developmentApiEnabled: Boolean = false,
    engine: HttpClientEngine = defaultHttpEngine(),
) : UnauthenticatedNetworkClientProvider {
    override val unauthenticatedNetworkClient by lazy {
        UnauthenticatedNetworkClient(engine, backendLinks, serverMetaDataManager, developmentApiEnabled)
    }
}

class UnauthenticatedNetworkContainerV0 constructor(
    backendLinks: ServerConfigDTO.Links,
    serverMetaDataManager: ServerMetaDataManager,
    developmentApiEnabled: Boolean = false
) : UnauthenticatedNetworkContainer,
    UnauthenticatedNetworkClientProvider by UnauthenticatedNetworkClientProviderImpl(
        backendLinks,
        serverMetaDataManager,
        developmentApiEnabled
    ) {
    override val loginApi: LoginApi get() = LoginApiImpl(unauthenticatedNetworkClient)
    override val registerApi: RegisterApi get() = RegisterApiImpl(unauthenticatedNetworkClient)
    override val sso: SSOLoginApi get() = SSOLoginApiImpl(unauthenticatedNetworkClient)
}
