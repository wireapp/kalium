package com.wire.kalium.network.http.extension

import com.wire.kalium.network.http.request.ByteChannelRequestBody
import io.ktor.client.call.UnsupportedContentTypeException
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.util.InternalAPI
import io.ktor.utils.io.writer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod
import kotlin.coroutines.CoroutineContext

@OptIn(InternalAPI::class)
internal fun HttpRequestData.convertToOkHttpRequest(callContext: CoroutineContext): Request {
    val builder = Request.Builder()

    with(builder) {
        url(url.toString())

        mergeHeaders(headers, body) { key, value ->
            if (key == HttpHeaders.ContentLength) return@mergeHeaders

            addHeader(key, value)
        }

        val bodyBytes = if (HttpMethod.permitsRequestBody(method.value)) {
            body.convertToOkHttpBody(callContext)
        } else null

        method(method.value, bodyBytes)
    }

    return builder.build()
}

@OptIn(DelicateCoroutinesApi::class)
internal fun OutgoingContent.convertToOkHttpBody(callContext: CoroutineContext): RequestBody = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().let {
        it.toRequestBody(null, 0, it.size)
    }

    is OutgoingContent.ReadChannelContent -> ByteChannelRequestBody(contentLength, callContext) { readFrom() }
    is OutgoingContent.WriteChannelContent -> {
        ByteChannelRequestBody(contentLength, callContext) {
            GlobalScope.writer(callContext) { writeTo(channel) }.channel
        }
    }

    is OutgoingContent.NoContent -> ByteArray(0).toRequestBody(null, 0, 0)
    else -> throw UnsupportedContentTypeException(this)
}
