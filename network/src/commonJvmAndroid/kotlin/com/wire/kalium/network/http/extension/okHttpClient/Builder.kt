package com.wire.kalium.network.http.extension.okHttpClient

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.convertLongTimeoutToLongWithInfiniteAsZero
import io.ktor.util.InternalAPI
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Update [OkHttpClient.Builder] setting timeout configuration taken from
 * [HttpTimeout.HttpTimeoutCapabilityConfiguration].
 */
@OptIn(InternalAPI::class)
internal fun OkHttpClient.Builder.setupTimeoutAttributes(
    timeoutAttributes: HttpTimeout.HttpTimeoutCapabilityConfiguration
): OkHttpClient.Builder {
    timeoutAttributes.connectTimeoutMillis?.let {
        connectTimeout(convertLongTimeoutToLongWithInfiniteAsZero(it), TimeUnit.MILLISECONDS)
    }
    timeoutAttributes.socketTimeoutMillis?.let {
        readTimeout(convertLongTimeoutToLongWithInfiniteAsZero(it), TimeUnit.MILLISECONDS)
        writeTimeout(convertLongTimeoutToLongWithInfiniteAsZero(it), TimeUnit.MILLISECONDS)
    }
    return this
}

