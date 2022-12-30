package com.wire.kalium.network.http.extension

import com.wire.kalium.network.http.OkHttpCallback
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal suspend fun OkHttpClient.execute(
    request: Request,
    requestData: HttpRequestData
): Response = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)

    call.enqueue(OkHttpCallback(requestData, continuation))

    continuation.invokeOnCancellation {
        call.cancel()
    }
}
