package com.wire.kalium.network.http.engine

import com.wire.kalium.network.http.request.ByteChannelRequestBody
import com.wire.kalium.network.http.socket.OkHttpWebsocketSession
import com.wire.kalium.network.http.config.OkHttpConfig
import com.wire.kalium.network.http.extension.convertToOkHttpRequest
import com.wire.kalium.network.http.extension.execute
import com.wire.kalium.network.http.extension.fromOkHttp
import com.wire.kalium.network.http.extension.okHttpClient.setupTimeoutAttributes
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.SocketTimeoutException
import io.ktor.client.plugins.websocket.WebSocketCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.isUpgradeRequest
import io.ktor.client.utils.clientDispatcher
import io.ktor.http.HttpStatusCode
import io.ktor.util.InternalAPI
import io.ktor.util.SilentSupervisor
import io.ktor.util.createLRUCache
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writer
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSink
import okio.BufferedSource
import okio.use
import java.io.Closeable
import java.net.SocketTimeoutException
import kotlin.coroutines.CoroutineContext

/**
 * A [HttpClientEngine] implementation that is overwritten due to the issue with cancellation of the request
 * that is writing to a [BufferedSink]. The behaviour when converting to OkHttpBody had to be overwritten
 * and is provided using [ByteChannelRequestBody].
 *  @see (https://github.com/wireapp/kalium/pull/1251) for more details
 */
@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
class KaliumHttpEngine(override val config: OkHttpConfig) : HttpClientEngineBase("kalium-ktor-okhttp") {
    companion object {
        /**
         * It's an artificial prototype object to be used to create actual clients and eliminate the following issue:
         * https://github.com/square/okhttp/issues/3372.
         */
        private val okHttpClientPrototype: OkHttpClient by lazy {
            OkHttpClient.Builder().build()
        }

        /**
         * Creates an [HttpClientEngine] using [KaliumHttpEngine] and [OkHttpConfig].
         *
         * @param block a lambda function that allows configuring the [OkHttpConfig] object.
         * @return an [HttpClientEngine] instance.
         */
        fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine =
            KaliumHttpEngine(OkHttpConfig().apply(block))

    }

    override val dispatcher: CoroutineDispatcher by lazy {
        Dispatchers.clientDispatcher(
            config.threadsCount,
            "kalium-ktor-okhttp-dispatcher"
        )
    }

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> =
        setOf(HttpTimeout, WebSocketCapability)

    private val requestsJob: CoroutineContext

    override val coroutineContext: CoroutineContext

    /**
     * Cache that keeps least recently used [OkHttpClient] instances.
     */
    private val clientCache = createLRUCache(::createOkHttpClient, {}, config.clientCacheSize)

    init {
        val parent = super.coroutineContext[Job]!!
        requestsJob = SilentSupervisor(parent)
        coroutineContext = super.coroutineContext + requestsJob

        @OptIn(ExperimentalCoroutinesApi::class)
        GlobalScope.launch(super.coroutineContext, start = CoroutineStart.ATOMIC) {
            try {
                requestsJob[Job]!!.join()
            } finally {
                clientCache.forEach { (_, client) ->
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                }
                @Suppress("BlockingMethodInNonBlockingContext")
                (dispatcher as Closeable).close()
            }
        }
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val engineRequest = data.convertToOkHttpRequest(callContext)

        val requestEngine = clientCache[data.getCapabilityOrNull(HttpTimeout)]
            ?: error("OkHttpClient can't be constructed because HttpTimeout plugin is not installed")

        return if (data.isUpgradeRequest()) {
            executeWebSocketRequest(requestEngine, engineRequest, callContext)
        } else {
            executeHttpRequest(requestEngine, engineRequest, callContext, data)
        }
    }

    override fun close() {
        super.close()
        (requestsJob[Job] as CompletableJob).complete()
    }

    private suspend fun executeWebSocketRequest(
        engine: OkHttpClient,
        engineRequest: Request,
        callContext: CoroutineContext
    ): HttpResponseData {
        val requestTime = GMTDate()
        val session = OkHttpWebsocketSession(
            engine,
            config.webSocketFactory ?: engine,
            engineRequest,
            callContext
        ).apply { start() }

        val originResponse = session.originResponse.await()
        return buildResponseData(originResponse, requestTime, session, callContext)
    }

    private suspend fun executeHttpRequest(
        engine: OkHttpClient,
        engineRequest: Request,
        callContext: CoroutineContext,
        requestData: HttpRequestData
    ): HttpResponseData {
        val requestTime = GMTDate()
        val response = engine.execute(engineRequest, requestData)

        val body = response.body
        callContext[Job]!!.invokeOnCompletion { body?.close() }

        val responseContent = body?.source()?.toChannel(callContext, requestData) ?: ByteReadChannel.Empty
        return buildResponseData(response, requestTime, responseContent, callContext)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun BufferedSource.toChannel(context: CoroutineContext, requestData: HttpRequestData): ByteReadChannel =
        GlobalScope.writer(context) {
            use { source ->
                var lastRead = 0
                while (source.isOpen && context.isActive && lastRead >= 0) {
                    channel.write { buffer ->
                        lastRead = try {
                            source.read(buffer)
                        } catch (cause: Throwable) {
                            throw mapExceptions(cause, requestData)
                        }
                    }
                }
            }
        }.channel

    private fun buildResponseData(
        response: Response,
        requestTime: GMTDate,
        body: Any,
        callContext: CoroutineContext
    ): HttpResponseData {
        val status = HttpStatusCode(response.code, response.message)
        val version = response.protocol.fromOkHttp()
        val headers = response.headers.fromOkHttp()

        return HttpResponseData(status, requestTime, headers, version, body, callContext)
    }

    private fun mapExceptions(cause: Throwable, request: HttpRequestData): Throwable = when (cause) {
        is SocketTimeoutException -> SocketTimeoutException(request, cause)
        else -> cause
    }

    private fun createOkHttpClient(timeoutExtension: HttpTimeout.HttpTimeoutCapabilityConfiguration?): OkHttpClient {
        val builder = (config.preconfigured ?: okHttpClientPrototype).newBuilder()

        builder.dispatcher(Dispatcher())
        builder.apply(config.config)
        config.proxy?.let { builder.proxy(it) }
        timeoutExtension?.let {
            builder.setupTimeoutAttributes(it)
        }

        return builder.build()
    }

}
