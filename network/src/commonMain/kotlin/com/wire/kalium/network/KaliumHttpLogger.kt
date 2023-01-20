package com.wire.kalium.network

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.network.utils.obfuscatePath
import com.wire.kalium.network.utils.obfuscatedJsonMessage
import com.wire.kalium.network.utils.toJsonElement
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job

internal class KaliumHttpLogger(
    private val level: LogLevel,
    private val logger: Logger
) {
    private val requestLog = mutableMapOf<String, Any>()
    private val responseLog = mutableMapOf<String, Any>()
    private val requestLoggedMonitor = Job()
    private val responseHeaderMonitor = Job()

    private val requestLogged = atomic(false)
    private val responseLogged = atomic(false)

    private var responseLogLevel: KaliumLogLevel = KaliumLogLevel.ERROR

    fun logRequest(request: HttpRequestBuilder): OutgoingContent? {

        requestLog["method"] = request.method.value
        requestLog["endpoint"] = obfuscatePath(Url(request.url))

        val content = request.body as OutgoingContent

        when {
            level.info -> {
                val obfuscatedHeaders = obfuscatedHeaders(request.headers.entries().map { it.key to it.value }).toMutableMap()
                content.contentLength?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders.putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                requestLog["headers"] = obfuscatedHeaders.toMap()
            }

            level.headers -> {

                val obfuscatedHeaders = obfuscatedHeaders(request.headers.entries().map { it.key to it.value }).toMutableMap()
                content.contentLength?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders.putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                requestLog["headers"] = obfuscatedHeaders.toMap()
            }

            level.body -> {
                val obfuscatedHeaders = obfuscatedHeaders(request.headers.entries().map { it.key to it.value }).toMutableMap()
                content.contentLength?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders.putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                requestLog["headers"] = obfuscatedHeaders.toMap()
            }
        }

        return null
    }

    fun logResponse(response: HttpResponse) {
        responseLog["method"] = response.call.request.method.value
        responseLog["endpoint"] = obfuscatePath(response.call.request.url)
        responseLog["status"] = response.status.value

        when {
            level.info -> {
                // Intentionally left empty
            }

            level.headers -> {
                val obfuscatedHeaders = obfuscatedHeaders(response.headers.entries().map { it.key to it.value }).toMutableMap()
                responseLog["headers"] = obfuscatedHeaders.toMap()
            }
        }

        responseLogLevel = if (response.status.value < HttpStatusCode.BadRequest.value) {
            KaliumLogLevel.VERBOSE
        } else if (response.status.value < HttpStatusCode.InternalServerError.value) {
            KaliumLogLevel.WARN
        } else {
            KaliumLogLevel.ERROR
        }

        responseHeaderMonitor.complete()
    }

    suspend fun logResponseException(request: HttpRequest, cause: Throwable) {
        requestLoggedMonitor.join()
        if (level.info) {
            kaliumLogger.v(
                """RESPONSE FAILURE:
                            |{"endpoint":"${obfuscatePath(request.url)}\",
                            | "method":"${request.method.value}",
                            |  "cause":"$cause"}
                            |  """.trimMargin()
            )
        }
    }

    suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel): Unit = with(logger) {
        responseHeaderMonitor.join()

        val text = content.tryReadText(contentType?.charset() ?: Charsets.UTF_8) ?: "\"response body omitted\""
        responseLog["Content-Type"] = contentType?.charset() ?: Charsets.UTF_8
        responseLog["Content"] = obfuscatedJsonMessage(text)
    }

    fun closeRequestLog() {
        if (!requestLogged.compareAndSet(false, true)) return

        try {
            val jsonElement = requestLog.toJsonElement()
            kaliumLogger.v("REQUEST: $jsonElement")
        } finally {
            requestLoggedMonitor.complete()
        }
    }

    suspend fun closeResponseLog() {
        if (!responseLogged.compareAndSet(false, true)) return

        requestLoggedMonitor.join()
        val jsonElement = responseLog.toJsonElement()
        val logString = "RESPONSE: $jsonElement"

        when (responseLogLevel) {
            KaliumLogLevel.VERBOSE -> kaliumLogger.v(logString)
            KaliumLogLevel.WARN -> kaliumLogger.w(logString)
            else -> kaliumLogger.e(logString)
        }
    }

    private fun obfuscatedHeaders(headers: List<Pair<String, List<String>>>): Map<String, String> =
        headers.associate {
            it.first to it.second.joinToString(",")
        }
}
