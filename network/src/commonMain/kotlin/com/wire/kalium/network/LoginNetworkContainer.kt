package com.wire.kalium.network

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.configuration.ServerConfigApiImp
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImpl
import com.wire.kalium.network.api.user.register.RegisterApi
import com.wire.kalium.network.api.user.register.RegisterApiImpl
import io.ktor.client.engine.HttpClientEngine

class LoginNetworkContainer(
    private val engine: HttpClientEngine = defaultHttpEngine(),
    kaliumLogLevel: KaliumLogLevel
) {

    val loginApi: LoginApi get() = LoginApiImpl(anonymousHttpClient)
    val serverConfigApi: ServerConfigApi get() = ServerConfigApiImp(anonymousHttpClient)
    val registerApi: RegisterApi get() = RegisterApiImpl(anonymousHttpClient)
    private val kaliumLogger = KaliumLogger(
        config = KaliumLogger.Config(
            severity = kaliumLogLevel,
            tag = "LoginNetworkContainer"
        )
    )

    internal val anonymousHttpClient by lazy {
        provideBaseHttpClient(engine, kaliumLogger, HttpClientOptions.NoDefaultHost)
    }
}
