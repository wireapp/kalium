package com.wire.kalium.network

import com.wire.kalium.network.api.auth.AccessTokenApi
import com.wire.kalium.network.api.auth.AccessTokenApiImpl
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
    // AccessTokenApi is private here because it's a bit special since getToken
    // can be called with and without the Authorization header and
    // in LoginNetworkContainer it only needed to generate a token after register
    // and no consumer of kalium should call getToken without the Authorization header
    private val accessTokenApi: AccessTokenApi get() = AccessTokenApiImpl(anonymousHttpClient)
    val registerApi: RegisterApi get() = RegisterApiImpl(anonymousHttpClient, accessTokenApi)

    internal val anonymousHttpClient by lazy {
        provideBaseHttpClient(engine, HttpClientOptions.NoDefaultHost)
    }
}
