package com.wire.kalium.network.http

import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.SocketTimeoutException
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.CancellableContinuation
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class OkHttpCallback(
    private val requestData: HttpRequestData,
    private val continuation: CancellableContinuation<Response>
) : Callback {

    override fun onFailure(call: Call, e: IOException) {
        if (continuation.isCancelled) {
            return
        }

        continuation.resumeWithException(mapOkHttpException(requestData, e))
    }

    override fun onResponse(call: Call, response: Response) {
        if (!call.isCanceled()) {
            continuation.resume(response)
        }
    }

    private fun mapOkHttpException(
        requestData: HttpRequestData,
        origin: IOException
    ): Throwable = when (val cause = origin.unwrapSuppressed()) {
        is SocketTimeoutException ->
            if (cause.isConnectException()) {
                ConnectTimeoutException(requestData, cause)
            } else {
                SocketTimeoutException(requestData, cause)
            }

        else -> cause
    }

    private fun IOException.isConnectException() =
        message?.contains("connect", ignoreCase = true) == true

    private fun IOException.unwrapSuppressed(): Throwable {
        if (suppressed.isNotEmpty()) return suppressed[0]
        return this
    }

}
