package com.wire.kalium.network.http.config

import io.ktor.client.engine.HttpClientEngineConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.WebSocket

class OkHttpConfig : HttpClientEngineConfig() {

    var config: OkHttpClient.Builder.() -> Unit = {
        followRedirects(false)
        followSslRedirects(false)

        retryOnConnectionFailure(true)
    }

    /**
     * Preconfigured [OkHttpClient] instance instead of configuring one.
     */
    var preconfigured: OkHttpClient? = null

    /**
     * Size of the cache that keeps least recently used [OkHttpClient] instances. Set "0" to avoid caching.
     */
    @Suppress("MagicNumber")
    var clientCacheSize: Int = 10

    /**
     * If provided, this [WebSocket.Factory] will be used to create [WebSocket] instances.
     * Otherwise, [OkHttpClient] is used directly.
     */
    var webSocketFactory: WebSocket.Factory? = null

    /**
     * Configure [OkHttpClient] using [OkHttpClient.Builder].
     */
    private fun config(block: OkHttpClient.Builder.() -> Unit) {
        val oldConfig = config
        config = {
            oldConfig()
            block()
        }
    }

    /**
     * Add [Interceptor] to [OkHttp] client.
     */
    fun addInterceptor(interceptor: Interceptor) {
        config {
            addInterceptor(interceptor)
        }
    }

    /**
     * Add network [Interceptor] to [OkHttp] client.
     */
    fun addNetworkInterceptor(interceptor: Interceptor) {
        config {
            addNetworkInterceptor(interceptor)
        }
    }
}
