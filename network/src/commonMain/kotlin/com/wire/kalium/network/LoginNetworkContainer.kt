package com.wire.kalium.network

import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.configuration.ServerConfigApiImp
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImpl
import com.wire.kalium.network.api.user.register.RegisterApi
import com.wire.kalium.network.api.user.register.RegisterApiImpl
import io.ktor.client.engine.HttpClientEngine

class LoginNetworkContainer(
    private val engine: HttpClientEngine = defaultHttpEngine()
) {

    val loginApi: LoginApi get() = LoginApiImpl(anonymousHttpClient)
    val serverConfigApi: ServerConfigApi get() = ServerConfigApiImp(anonymousHttpClient)
    val registerApi: RegisterApi get() = RegisterApiImpl(anonymousHttpClient)

    internal val anonymousHttpClient by lazy {
        provideBaseHttpClient(engine, HttpClientOptions.NoDefaultHost)
    }
}
