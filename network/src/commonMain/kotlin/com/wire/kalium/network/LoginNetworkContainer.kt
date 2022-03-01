package com.wire.kalium.network

import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.configuration.ServerConfigApiImp
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImpl
import io.ktor.client.engine.HttpClientEngine

class LoginNetworkContainer(
    private val engine: HttpClientEngine = defaultHttpEngine(),
    private val isRequestLoggingEnabled: Boolean = false
) {

    val loginApi: LoginApi get() = LoginApiImpl(anonymousHttpClient)
    val serverConfigApi: ServerConfigApi get() = ServerConfigApiImp(anonymousHttpClient)

    internal val anonymousHttpClient by lazy {
        provideBaseHttpClient(engine, isRequestLoggingEnabled, HttpClientOptions.NoDefaultHost)
    }
}
