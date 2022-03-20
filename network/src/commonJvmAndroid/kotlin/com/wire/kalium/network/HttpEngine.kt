package com.wire.kalium.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Interceptor
import okhttp3.Response

actual fun defaultHttpEngine(): HttpClientEngine {
    return OkHttp.create {
        /**
         * This is a hack
         *
         * As seen here: https://github.com/ktorio/ktor/issues/1127
         * Ktor is pretty has `application/protobuf` but not `application/x-protobuf`
         * and it doesn't support custom content types!
         */
        addInterceptor(object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val currentRequest = chain.call().request()
                val currentHeaders = chain.call().request().headers
                // When sending a byteArray, Ktor will put `octet-stream`. We can identify this and replace with protobuf
                val isProtoBuf = currentHeaders["Content-Type"] == "application/octet-stream"

                return if (!isProtoBuf) {
                    chain.proceed(currentRequest)
                } else {
                    val newRequest = currentRequest.newBuilder()
                        .header("Content-Type", "application/x-protobuf")
                        .build()
                    chain.proceed(newRequest)
                }
            }
        })
        addInterceptor(RefreshTokenWorkAround())
    }
}

class RefreshTokenWorkAround : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.call().request().let { request ->
            chain.proceed(request)
        }.let { response ->
            when (response.code) {
                401 -> response.newBuilder().addHeader("WWW-Authenticate", "Bearer").build()
                else -> response
            }
        }
    }
}
