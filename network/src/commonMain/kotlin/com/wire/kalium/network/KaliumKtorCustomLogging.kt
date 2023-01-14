package com.wire.kalium.network

import com.wire.kalium.network.utils.obfuscatePath
import com.wire.kalium.network.utils.obfuscatedJsonMessage
import com.wire.kalium.network.utils.toJsonElement
import com.wire.kalium.util.KaliumDispatcherImpl
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.readText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * A client's logging plugin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooGenericExceptionCaught", "EmptyFinallyBlock")
public class KaliumKtorCustomLogging private constructor(
    public val logger: Logger,
    public var level: LogLevel,
    private val singleThreadContext: CoroutineContext = KaliumDispatcherImpl.default.limitedParallelism(1),
    public var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList()
) {
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

    private suspend fun logRequest(request: HttpRequestBuilder): OutgoingContent? = withContext(singleThreadContext) {

        val properties = mutableMapOf<String, Any>(
            "method" to request.method.value,
            "endpoint" to obfuscatePath(Url(request.url)),
        )

        val content = request.body as OutgoingContent

        when {
            level.info -> {
                val obfuscatedHeaders = obfuscatedHeaders(request.headers.entries().map { it.key to it.value }).toMutableMap()
                content.contentLength?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders.putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                properties["headers"] = obfuscatedHeaders.toMap()
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
                val obfuscatedHeaders = obfuscatedHeaders(request.headers.entries().map { it.key to it.value }).toMutableMap()
                content.contentLength?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders.putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                properties["headers"] = obfuscatedHeaders.toMap()

                val jsonElement = properties.toJsonElement()

                kaliumLogger.v("REQUEST: $jsonElement")
            }
        }
        return@withContext null
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

        if (response.status.value < HttpStatusCode.BadRequest.value) {
            kaliumLogger.v(logString)
        } else if (response.status.value < HttpStatusCode.InternalServerError.value) {
            kaliumLogger.w(logString)
        } else {
            kaliumLogger.e(logString)
        }
    }

    private fun obfuscatedHeaders(headers: List<Pair<String, List<String>>>): Map<String, String> =
        headers.associate {
            it.first to it.second.joinToString(",")
        }

    private suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel) {
        content.tryReadText(contentType?.charset() ?: Charsets.UTF_8)?.also {
            kaliumLogger.v("RESPONSE BODY: {\"Content-Type\":\"${contentType}\", \"Content\":${obfuscatedJsonMessage(it)}}")
        } ?: kaliumLogger.v("RESPONSE BODY: {\"Content-Type\":\"${contentType}\", \"Content\":\"response body omitted\"}")
    }

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            kaliumLogger.v(
                """REQUEST FAILURE: {
                        |"endpoint":"${obfuscatePath(Url(context.url))}",
                        | "method":"${context.method.value}",
                        |  "cause":"$cause"
                        |  """.trimMargin()
            )
        }
    }

    private fun logResponseException(request: HttpRequest, cause: Throwable) {
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

    public companion object : HttpClientPlugin<Config, KaliumKtorCustomLogging> {
        override val key: AttributeKey<KaliumKtorCustomLogging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): KaliumKtorCustomLogging {
            val config = Config().apply(block)
            return KaliumKtorCustomLogging(
                logger = config.logger,
                level = config.level,
                filters = config.filters
            )
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: KaliumKtorCustomLogging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                val response = if (plugin.filters.isEmpty() || plugin.filters.any { it(context) }) {
                    try {
                        plugin.logRequest(context)
                    } catch (_: Throwable) {
                        null
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
                    plugin.logResponse(response.call.response)
                    proceedWith(subject)
                } catch (cause: Throwable) {
                    plugin.logResponseException(response.call.request, cause)
                    throw cause
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
