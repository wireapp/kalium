package com.wire.kalium.network

import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

actual fun defaultHttpEngine(serverConfigDTOLinks: ServerConfigDTO.Links?): HttpClientEngine = OkHttp.create {
    // OkHttp doesn't support configuring ping intervals dynamically,
    // so they must be set when creating the Engine
    // See https://youtrack.jetbrains.com/issue/KTOR-4752
    val isProxyRequired = serverConfigDTOLinks?.proxy != null
    if (isProxyRequired) {
        val proxy = Proxy(
            Proxy.Type.SOCKS,
            serverConfigDTOLinks?.proxy?.port?.let { InetSocketAddress.createUnresolved(serverConfigDTOLinks.proxy.apiProxy, it) }
        )
        val client = OkHttpClient.Builder().pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS).proxy(proxy).build()
        preconfigured = client
        webSocketFactory = KaliumWebSocketFactory(client)
    } else {
        val client = OkHttpClient.Builder().pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS).build()
        preconfigured = client
        webSocketFactory = KaliumWebSocketFactory(client)
    }
}
