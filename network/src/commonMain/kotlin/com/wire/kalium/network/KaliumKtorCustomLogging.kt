package com.wire.kalium.network

import com.wire.kalium.network.utils.obfuscatedJsonMessage
import com.wire.kalium.network.utils.obfuscatePath
import com.wire.kalium.network.utils.sensitiveJsonKeys
import com.wire.kalium.network.utils.toJsonElement
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * A client's logging plugin.
 */
@Suppress("TooGenericExceptionCaught", "EmptyFinallyBlock")
public class KaliumKtorCustomLogging private constructor(
    public val logger: Logger,
    public var level: LogLevel,
    public var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList()
) {

    private val mutex = Mutex()

    /**
     * [Logging] plugin configuration
     */
    public class Config {
        /**
         * filters
         */
        internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()

        /**
         * [Logger] instance to use
         */
        public var logger: Logger = Logger.DEFAULT

        /**
         * log [LogLevel]
         */
        public var level: LogLevel = LogLevel.HEADERS

        /**
         * Log messages for calls matching a [predicate]
         */
        public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
            filters.add(predicate)
        }
    }

    private suspend fun beginLogging() {
        mutex.lock()
    }

    private fun doneLogging() {
        mutex.unlock()
    }

    private suspend fun logRequest(request: HttpRequestBuilder): OutgoingContent? {

        val properties = mutableMapOf<String, Any>(
            "method" to request.method.value,
            "endpoint" to obfuscatePath(Url(request.url)),
        )

        val content = request.body as OutgoingContent

        when {
            level.info -> {
                val jsonElement = properties.toJsonElement()
                kaliumLogger.v("REQUEST: $jsonElement")
            }
            level.headers -> {

                val obfuscatedHeaders = obfuscatedHeaders(request.headers.entries().map { it.key to it.value }).toMutableMap()
                content.contentLength?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders.putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                properties["headers"] = obfuscatedHeaders.toMap()

                val jsonElement = properties.toJsonElement()

                kaliumLogger.v("REQUEST: $jsonElement")
            }
            level.body -> {
                return logRequestBody(content)
            }
        }
        return null
    }

    private fun logResponse(response: HttpResponse) {

        val properties = mutableMapOf<String, Any>(
            "method" to response.call.request.method.value,
            "endpoint" to obfuscatePath(response.call.request.url),
            "status" to response.status.value,
        )

        when {
            level.info -> {
                // Intentionally left empty
            }
            level.headers -> {
                val obfuscatedHeaders = obfuscatedHeaders(response.headers.entries().map { it.key to it.value }).toMutableMap()
                properties["headers"] = obfuscatedHeaders.toMap()
            }
        }

        val jsonElement = properties.toJsonElement()
        val logString = "RESPONSE: $jsonElement"

        if (response.status.value < 400) {
            kaliumLogger.v(logString)
        }
        else if (response.status.value < 500) {
            kaliumLogger.w(logString)
        }
        else {
            kaliumLogger.e(logString)
        }
    }
    private fun obfuscatedHeaders(headers: List<Pair<String, List<String>>>): Map<String, String> =
        headers.associate {
            if (sensitiveJsonKeys.contains(it.first.lowercase())) {
                it.first to "***"
            } else {
                it.first to it.second.joinToString(",")
            }
        }

    private suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel): Unit = with(logger) {
        val text = content.tryReadText(contentType?.charset() ?: Charsets.UTF_8) ?: "\"response body omitted\""
        kaliumLogger.v("RESPONSE BODY: {\"Content-Type\":\"${contentType}\", \"Content\":${obfuscatedJsonMessage(text)}}")
    }

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            kaliumLogger.v("REQUEST FAILURE: {\"endpoint\":\"${obfuscatePath(Url(context.url))}\", \"cause\":\"$cause\"}")
        }
    }

    private fun logResponseException(request: HttpRequest, cause: Throwable) {
        if (level.info) {
            kaliumLogger.v("RESPONSE FAILURE: {\"endpoint\":\"${obfuscatePath(request.url)}\", \"cause\":\"$cause\"}")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun logRequestBody(content: OutgoingContent): OutgoingContent? {

        val charset = content.contentType?.charset() ?: Charsets.UTF_8

        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Unconfined) {
            val text = channel.tryReadText(charset) ?: "\"request body omitted\""
            kaliumLogger.v("REQUEST BODY: {\"Content-Type\":\"${content.contentType}\", \"content\":${obfuscatedJsonMessage(text)}}")
        }

        return content.observe(channel)
    }

    public companion object : HttpClientPlugin<Config, KaliumKtorCustomLogging> {
        override val key: AttributeKey<KaliumKtorCustomLogging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): KaliumKtorCustomLogging {
            val config = Config().apply(block)
            return KaliumKtorCustomLogging(config.logger, config.level, config.filters)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: KaliumKtorCustomLogging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                val response = if (plugin.filters.isEmpty() || plugin.filters.any { it(context) }) {
                    try {
                        plugin.beginLogging()
                        plugin.logRequest(context)
                    } catch (_: Throwable) {
                        null
                    } finally {
                        plugin.doneLogging()
                    }
                } else null

                try {
                    proceedWith(response ?: subject)
                } catch (cause: Throwable) {
                    plugin.logRequestException(context, cause)
                    throw cause
                } finally {
                }
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                try {
                    plugin.beginLogging()
                    plugin.logResponse(response.call.response)
                    proceedWith(subject)
                } catch (cause: Throwable) {
                    plugin.logResponseException(response.call.request, cause)
                    throw cause
                } finally {
                    if (!plugin.level.body) {
                        plugin.doneLogging()
                    }
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                try {
                    proceed()
                } catch (cause: Throwable) {
                    plugin.logResponseException(context.request, cause)
                    throw cause
                }
            }

            if (!plugin.level.body) {
                return
            }

            val observer: ResponseHandler = {
                try {
                    plugin.logResponseBody(it.contentType(), it.content)
                } catch (_: Throwable) {
                } finally {
                    plugin.doneLogging()
                }
            }
            ResponseObserver.install(ResponseObserver(observer), scope)
        }
    }
}

/**
 * Configure and install [Logging] in [HttpClient].
 */
@Suppress("FunctionNaming")
public fun HttpClientConfig<*>.Logging(block: Logging.Config.() -> Unit = {}) {
    install(Logging, block)
}

@Suppress("TooGenericExceptionCaught")
internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? = try {
    readRemaining().readText(charset = charset)
} catch (cause: Throwable) {
    null
}
