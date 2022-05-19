package com.wire.kalium.network

import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.configuration.ServerConfigApiImpl
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImpl
import com.wire.kalium.network.api.user.login.SSOLoginApi
import com.wire.kalium.network.api.user.login.SSOLoginApiImpl
import com.wire.kalium.network.api.user.register.RegisterApi
import com.wire.kalium.network.api.user.register.RegisterApiImpl
import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.network.api.versioning.VersionApiImpl
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

class UnauthenticatedNetworkContainer constructor(
    serverConfigDTO: ServerConfigDTO, engine: HttpClientEngine = defaultHttpEngine()
) {
    internal val unauthenticatedNetworkClient by lazy {
        UnauthenticatedNetworkClient(engine, serverConfigDTO)
    }

    val loginApi: LoginApi get() = LoginApiImpl(unauthenticatedNetworkClient)
    val registerApi: RegisterApi get() = RegisterApiImpl(unauthenticatedNetworkClient)
    val sso: SSOLoginApi get() = SSOLoginApiImpl(unauthenticatedNetworkClient)

    // TODO(network): create new container "UnboundNetworkContainer"
    val serverConfigApi: ServerConfigApi get() = ServerConfigApiImpl(unauthenticatedNetworkClient)
    val remoteVersion: VersionApi get() = VersionApiImpl(unauthenticatedNetworkClient)
}
