package com.wire.kalium.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

actual fun defaultHttpEngine(): HttpClientEngine {
    return OkHttp.create {
        addInterceptor(object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                return chain.request().let { checkedRequest -> chain.proceed(checkedRequest) }.handleUnauthorizedResponse()
            }
        })
    }
}

/**
 * Ktor need "WWW-Authenticate" to be set by BE in-order for the tokens refresh to work
 * see issue https://youtrack.jetbrains.com/issue/KTOR-2806
 * BE does not set "WWW-Authenticate"
 *
 * checks for 401 response -> add WWW-Authenticate header
 */
private fun Response.handleUnauthorizedResponse(): Response =
    when (this.code == 401) {
        true -> this.newBuilder().addHeader("WWW-Authenticate", "Bearer").build()
        false -> this
    }
