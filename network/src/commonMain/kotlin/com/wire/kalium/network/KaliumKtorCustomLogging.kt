/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.utils.obfuscatePath
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.readText

private val KaliumHttpCustomLogger = AttributeKey<KaliumHttpLogger>("KaliumHttpLogger")
private val DisableLogging = AttributeKey<Unit>("DisableLogging")

/**
 * A client's logging plugin.
 */
@Suppress("TooGenericExceptionCaught", "EmptyFinallyBlock")
class KaliumKtorCustomLogging private constructor(
    val logger: Logger,
    val kaliumLogger: KaliumLogger,
    var level: LogLevel,
    var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList()
) {

    /**
     * [Logging] plugin configuration
     */
    class Config {
        /**
         * filters
         */
        internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()

        private var _logger: Logger? = null

        /**
         * [Logger] instance to use
         */
        var logger: Logger
            get() = _logger ?: Logger.DEFAULT
            set(value) {
                _logger = value
            }

        /**
         * log [LogLevel]
         */
        var level: LogLevel = LogLevel.HEADERS

        /**
         * [KaliumLogger] instance to use
         */
        var kaliumLogger: KaliumLogger = com.wire.kalium.network.kaliumLogger

        /**
         * Log messages for calls matching a [predicate]
         */
        fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
            filters.add(predicate)
        }
    }

    private fun setupRequestLogging(client: HttpClient) {
        client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
            if (!shouldBeLogged(context)) {
                context.attributes.put(DisableLogging, Unit)
                return@intercept
            }

            val response = try {
                logRequest(context)
            } catch (_: Throwable) {
                null
            }

            try {
                proceedWith(response ?: subject)
            } catch (cause: Throwable) {
                logRequestException(context, cause)
                throw cause
            } finally {
            }
        }
    }

    @OptIn(InternalAPI::class)
    private fun setupResponseLogging(client: HttpClient) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
            if (level == LogLevel.NONE || response.call.attributes.contains(DisableLogging)) return@intercept

            val logger = response.call.attributes[KaliumHttpCustomLogger]

            var failed = false
            try {
                logger.logResponse(response.call.response)
                proceedWith(subject)
            } catch (cause: Throwable) {
                logger.logResponseException(response.call.request, cause)
                failed = true
                throw cause
            } finally {
                if (failed || !level.body) logger.closeResponseLog()
            }
        }

        client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
            if (level == LogLevel.NONE || context.attributes.contains(DisableLogging)) {
                return@intercept
            }

            try {
                proceed()
            } catch (cause: Throwable) {
                val logger = context.attributes[KaliumHttpCustomLogger]
                logger.logResponseException(context.request, cause)
                logger.closeResponseLog()
                throw cause
            }
        }

        if (!level.body) return

        val observer: ResponseHandler = observer@{
            if (level == LogLevel.NONE || it.call.attributes.contains(DisableLogging)) {
                return@observer
            }

            val logger = it.call.attributes[KaliumHttpCustomLogger]
            try {
                logger.logResponseBody(it.contentType(), it.content)
            } catch (_: Throwable) {
            } finally {
                logger.closeResponseLog()
            }
        }
        ResponseObserver.install(ResponseObserver(observer), client)
    }

    private fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
        val logger = KaliumHttpLogger(level, kaliumLogger)
        request.attributes.put(KaliumHttpCustomLogger, logger)

        logger.logRequest(request)

        logger.closeRequestLog()

        return null
    }

    companion object : HttpClientPlugin<Config, KaliumKtorCustomLogging> {
        override val key: AttributeKey<KaliumKtorCustomLogging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): KaliumKtorCustomLogging {
            val config = Config().apply(block)
            return KaliumKtorCustomLogging(config.logger, config.kaliumLogger, config.level, config.filters)
        }

        override fun install(plugin: KaliumKtorCustomLogging, scope: HttpClient) {
            plugin.setupRequestLogging(scope)
            plugin.setupResponseLogging(scope)
        }
    }

    private fun shouldBeLogged(request: HttpRequestBuilder): Boolean = filters.isEmpty() || filters.any { it(request) }

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            kaliumLogger.v(
                """REQUEST FAILURE: {
                        |"endpoint":"${obfuscatePath(Url(context.url))}",
                        | "method":"${context.method.value}",
                        |  "cause":"$cause"}
                        |  """.trimMargin()
            )
        }
    }
}

/**
 * Configure and install [Logging] in [HttpClient].
 */
@Suppress("FunctionNaming")
fun HttpClientConfig<*>.Logging(block: Logging.Config.() -> Unit = {}) {
    install(Logging, block)
}

@Suppress("TooGenericExceptionCaught")
internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? = try {
    readRemaining().readText(charset = charset)
} catch (cause: Throwable) {
    null
}
