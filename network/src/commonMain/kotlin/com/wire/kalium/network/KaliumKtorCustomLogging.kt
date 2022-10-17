package com.wire.kalium.network

import com.wire.kalium.network.utils.obfuscateAndLogMessage
import com.wire.kalium.network.utils.obfuscatePath
import com.wire.kalium.network.utils.sensitiveJsonKeys
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
        if (level.info) {
            kaliumLogger.v("REQUEST: ${Url(request.url)} ")
            kaliumLogger.v("REQUEST: ${obfuscatePath(Url(request.url))} ")
            kaliumLogger.v("METHOD: ${request.method}")
        }

        val content = request.body as OutgoingContent

        if (level.headers) {
            kaliumLogger.v("COMMON HEADERS")
            logHeaders(request.headers.entries())

            kaliumLogger.v("CONTENT HEADERS")
            content.contentLength?.let { logger.logHeader(HttpHeaders.ContentLength, it.toString()) }
            content.contentType?.let { logger.logHeader(HttpHeaders.ContentType, it.toString()) }
            logHeaders(content.headers.entries())
        }

        return if (level.body) {
            logRequestBody(content)
        } else null
    }

    private fun logResponse(response: HttpResponse) {
        if (level.info) {
            kaliumLogger.v("RESPONSE: ${response.status}")
            kaliumLogger.v("METHOD: ${response.call.request.method}")
            kaliumLogger.v("FROM: ${obfuscatePath(response.call.request.url)}")
        }

        if (level.headers) {
            kaliumLogger.v("COMMON HEADERS")
            logHeaders(response.headers.entries())
        }
    }

    private suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel): Unit = with(logger) {
        kaliumLogger.v("BODY Content-Type: $contentType")
        kaliumLogger.v("BODY START")
        val message = content.tryReadText(contentType?.charset() ?: Charsets.UTF_8) ?: "[response body omitted]"
        obfuscateAndLogMessage(message)
        kaliumLogger.v("BODY END")
    }

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            kaliumLogger.v("REQUEST ${obfuscatePath(Url(context.url))} failed with exception: $cause")
        }
    }

    private fun logResponseException(request: HttpRequest, cause: Throwable) {
        if (level.info) {
            kaliumLogger.v("RESPONSE ${obfuscatePath(request.url)} failed with exception: $cause")
        }
    }

    private fun logHeaders(headers: Set<Map.Entry<String, List<String>>>) {
        val sortedHeaders = headers.toList().sortedBy { it.key }

        sortedHeaders.forEach { (key, values) ->
            logger.logHeader(key, values.joinToString("; "))
        }
    }

    private fun Logger.logHeader(key: String, value: String) {
        if (sensitiveJsonKeys.contains(key.lowercase())) {
            kaliumLogger.v("-> $key: *******")
        } else {
            kaliumLogger.v("-> $key: $value")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun logRequestBody(content: OutgoingContent): OutgoingContent? {
        kaliumLogger.v("BODY Content-Type: ${content.contentType}")

        val charset = content.contentType?.charset() ?: Charsets.UTF_8

        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Unconfined) {
            val text = channel.tryReadText(charset) ?: "[request body omitted]"
            kaliumLogger.v("BODY START")
            obfuscateAndLogMessage(text)
            kaliumLogger.v("BODY END")
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
