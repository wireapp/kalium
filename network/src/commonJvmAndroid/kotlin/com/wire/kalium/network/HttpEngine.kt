package com.wire.kalium.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

actual fun defaultHttpEngine(): HttpClientEngine = OkHttp.create {
    // OkHttp doesn't support configuring ping intervals dynamically,
    // so they must be set when creating the Engine
    // See https://youtrack.jetbrains.com/issue/KTOR-4752
    val client = OkHttpClient.Builder().pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS).build()
    preconfigured = client
    webSocketFactory = KaliumWebSocketFactory(client)
}
