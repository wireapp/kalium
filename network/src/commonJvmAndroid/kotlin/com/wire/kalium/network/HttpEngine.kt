package com.wire.kalium.network

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.tools.isProxyRequired
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit

actual fun defaultHttpEngine(
    serverConfigDTOProxy: ServerConfigDTO.Proxy?,
    proxyCredentials: ProxyCredentialsDTO?
): HttpClientEngine = OkHttp.create {
    // OkHttp doesn't support configuring ping intervals dynamically,
    // so they must be set when creating the Engine
    // See https://youtrack.jetbrains.com/issue/KTOR-4752
    if (isProxyRequired(serverConfigDTOProxy)) {
        if (serverConfigDTOProxy?.isProxyNeedsAuthentication == true) {
            if (proxyCredentials == null) throw error("Credentials does not exist")
            with(proxyCredentials) {
                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(username, password?.toCharArray())
                    }
                })
            }
        }

        val proxy = Proxy(
            Proxy.Type.SOCKS,
            serverConfigDTOProxy.proxyPort.let { InetSocketAddress.createUnresolved(serverConfigDTOProxy.apiProxy, it) }
        )

        val client = OkHttpClient.Builder().pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS).proxy(proxy)
            .build()
        preconfigured = client
        webSocketFactory = KaliumWebSocketFactory(client)

    } else {
        val client = OkHttpClient.Builder().pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS).build()
        preconfigured = client
        webSocketFactory = KaliumWebSocketFactory(client)
    }
}
