package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.ServerMetaDataManager
import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.unAuthenticated.LoginApi
import com.wire.kalium.network.api.base.unAuthenticated.SSOLogin
import com.wire.kalium.network.api.base.unAuthenticated.register.RegisterApi
import com.wire.kalium.network.api.v0.unauthenticated.LoginApiV0
import com.wire.kalium.network.api.v0.unauthenticated.RegisterApiV0
import com.wire.kalium.network.api.v0.unauthenticated.SSOLoginV0
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

interface UnauthenticatedNetworkContainer {
    val loginApi: LoginApi
    val registerApi: RegisterApi
    val sso: SSOLogin
}

internal interface UnauthenticatedNetworkClientProvider {
    val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
}

internal class UnauthenticatedNetworkClientProviderImpl internal constructor(
    backendLinks: ServerConfigDTO.Links,
    serverMetaDataManager: ServerMetaDataManager,
    developmentApiEnabled: Boolean = false,
    engine: HttpClientEngine = defaultHttpEngine(),
) : UnauthenticatedNetworkClientProvider {
    override val unauthenticatedNetworkClient by lazy {
        UnauthenticatedNetworkClient(engine, backendLinks, serverMetaDataManager, developmentApiEnabled)
    }
}
