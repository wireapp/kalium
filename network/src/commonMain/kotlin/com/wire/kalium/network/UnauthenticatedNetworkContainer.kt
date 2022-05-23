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
    backendLinks: ServerConfigDTO.Links, engine: HttpClientEngine = defaultHttpEngine()
) {
    internal val unauthenticatedNetworkClient by lazy {
        UnauthenticatedNetworkClient(engine, backendLinks, TODO())
    }

    val loginApi: LoginApi get() = LoginApiImpl(unauthenticatedNetworkClient)
    val registerApi: RegisterApi get() = RegisterApiImpl(unauthenticatedNetworkClient)
    val sso: SSOLoginApi get() = SSOLoginApiImpl(unauthenticatedNetworkClient)


}


class UnboundNetworkContainer(
    engine: HttpClientEngine = defaultHttpEngine()
) {
    internal val unboundNetworkClient by lazy {
        UnboundNetworkClient(engine)
    }

    val serverConfigApi: ServerConfigApi get() = ServerConfigApiImpl(unboundNetworkClient)
    val remoteVersion: VersionApi get() = VersionApiImpl(unboundNetworkClient)
}
