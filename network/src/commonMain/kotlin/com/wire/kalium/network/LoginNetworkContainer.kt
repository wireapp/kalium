package com.wire.kalium.network

import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImp
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.json.serializer.KotlinxSerializer

class LoginNetworkContainer(
    private val engine: HttpClientEngine = defaultHttpEngine(),
    private val isRequestLoggingEnabled: Boolean = false
) {

    val loginApi: LoginApi get() = LoginApiImp(anonymousHttpClient)

    private val kotlinxSerializer = KotlinxSerializer(KtxSerializer.json)

    internal val anonymousHttpClient by lazy {
        provideBaseHttpClient(kotlinxSerializer, engine, isRequestLoggingEnabled)
    }
}
