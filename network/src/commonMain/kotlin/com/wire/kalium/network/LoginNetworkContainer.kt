package com.wire.kalium.network

import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImpl
import com.wire.kalium.network.tools.BackendConfig
import io.ktor.client.engine.HttpClientEngine

class LoginNetworkContainer(
    private val engine: HttpClientEngine = defaultHttpEngine(),
    private val isRequestLoggingEnabled: Boolean = false
) {

    val loginApi: LoginApi get() = LoginApiImpl(anonymousHttpClient)

    internal val anonymousHttpClient by lazy {
        provideBaseHttpClient(engine, isRequestLoggingEnabled, HttpClientOptions.NoDefaultHost)
    }
}
