package com.wire.kalium.network

import io.ktor.client.call.UnsupportedContentTypeException
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.SocketTimeoutException
import io.ktor.client.plugins.convertLongTimeoutToLongWithInfiniteAsZero
import io.ktor.client.plugins.websocket.WebSocketCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.isUpgradeRequest
import io.ktor.client.utils.clientDispatcher
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
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
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.internal.http.HttpMethod
import java.io.Closeable
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import io.ktor.client.plugins.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import okhttp3.*
import okio.*
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.*

private class OkHttpCallback(
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

@Suppress("KDocMissingDocumentation")
@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
public class KaliumHttpEngine(override val config: OkHttpConfig) : HttpClientEngineBase("ktor-okhttp") {

    public override val dispatcher: CoroutineDispatcher by lazy {
        Dispatchers.clientDispatcher(
            config.threadsCount,
            "ktor-okhttp-dispatcher"
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

    internal fun Headers.fromOkHttp(): io.ktor.http.Headers = object : io.ktor.http.Headers {
        override val caseInsensitiveName: Boolean = true

        override fun getAll(name: String): List<String>? = this@fromOkHttp.values(name).takeIf { it.isNotEmpty() }

        override fun names(): Set<String> = this@fromOkHttp.names()

        override fun entries(): Set<Map.Entry<String, List<String>>> = this@fromOkHttp.toMultimap().entries

        override fun isEmpty(): Boolean = this@fromOkHttp.size == 0
    }

    @Suppress("DEPRECATION")
    internal fun Protocol.fromOkHttp(): HttpProtocolVersion = when (this) {
        Protocol.HTTP_1_0 -> HttpProtocolVersion.HTTP_1_0
        Protocol.HTTP_1_1 -> HttpProtocolVersion.HTTP_1_1
        Protocol.SPDY_3 -> HttpProtocolVersion.SPDY_3
        Protocol.HTTP_2 -> HttpProtocolVersion.HTTP_2_0
        Protocol.H2_PRIOR_KNOWLEDGE -> HttpProtocolVersion.HTTP_2_0
        Protocol.QUIC -> HttpProtocolVersion.QUIC
    }



     companion object {
        /**
         * It's an artificial prototype object to be used to create actual clients and eliminate the following issue:
         * https://github.com/square/okhttp/issues/3372.
         */
        private val okHttpClientPrototype: OkHttpClient by lazy {
            OkHttpClient.Builder().build()
        }

         fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine =
             KaliumHttpEngine(OkHttpConfig().apply(block))

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


@OptIn(InternalAPI::class)
private fun HttpRequestData.convertToOkHttpRequest(callContext: CoroutineContext): Request {
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
        ByteChannelRequestBody(contentLength, callContext) { GlobalScope.writer(callContext) { writeTo(channel) }.channel }
    }

    is OutgoingContent.NoContent -> ByteArray(0).toRequestBody(null, 0, 0)
    else -> throw UnsupportedContentTypeException(this)
}


/**
 * Update [OkHttpClient.Builder] setting timeout configuration taken from
 * [HttpTimeout.HttpTimeoutCapabilityConfiguration].
 */
@OptIn(InternalAPI::class)
private fun OkHttpClient.Builder.setupTimeoutAttributes(
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

class OkHttpConfig : HttpClientEngineConfig() {

    var config: OkHttpClient.Builder.() -> Unit = {
        followRedirects(false)
        followSslRedirects(false)

        retryOnConnectionFailure(true)
    }

    /**
     * Preconfigured [OkHttpClient] instance instead of configuring one.
     */
    public var preconfigured: OkHttpClient? = null

    /**
     * Size of the cache that keeps least recently used [OkHttpClient] instances. Set "0" to avoid caching.
     */
    public var clientCacheSize: Int = 10

    /**
     * If provided, this [WebSocket.Factory] will be used to create [WebSocket] instances.
     * Otherwise, [OkHttpClient] is used directly.
     */
    public var webSocketFactory: WebSocket.Factory? = null

    /**
     * Configure [OkHttpClient] using [OkHttpClient.Builder].
     */
    public fun config(block: OkHttpClient.Builder.() -> Unit) {
        val oldConfig = config
        config = {
            oldConfig()
            block()
        }
    }

    /**
     * Add [Interceptor] to [OkHttp] client.
     */
    public fun addInterceptor(interceptor: Interceptor) {
        config {
            addInterceptor(interceptor)
        }
    }

    /**
     * Add network [Interceptor] to [OkHttp] client.
     */
    public fun addNetworkInterceptor(interceptor: Interceptor) {
        config {
            addNetworkInterceptor(interceptor)
        }
    }
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

private fun mapExceptions(cause: Throwable, request: HttpRequestData): Throwable = when (cause) {
    is SocketTimeoutException -> SocketTimeoutException(request, cause)
    else -> cause
}


internal class OkHttpWebsocketSession(
    private val engine: OkHttpClient,
    private val webSocketFactory: WebSocket.Factory,
    engineRequest: Request,
    override val coroutineContext: CoroutineContext
) : DefaultWebSocketSession, WebSocketListener() {
    // Deferred reference to "this", completed only after the object successfully constructed.
    private val self = CompletableDeferred<OkHttpWebsocketSession>()

    internal val originResponse: CompletableDeferred<Response> = CompletableDeferred()

    override var pingIntervalMillis: Long
        get() = engine.pingIntervalMillis.toLong()
        set(_) = throw WebSocketException(
            "OkHttp doesn't support dynamic ping interval. You could switch it in the engine configuration."
        )

    override var timeoutMillis: Long
        get() = engine.readTimeoutMillis.toLong()
        set(_) = throw WebSocketException("Websocket timeout should be configured in OkHttpEngine.")

    override var masking: Boolean
        get() = true
        set(_) = throw WebSocketException("Masking switch is not supported in OkHttp engine.")

    override var maxFrameSize: Long
        get() = throw WebSocketException("OkHttp websocket doesn't support max frame size.")
        set(_) = throw WebSocketException("Websocket timeout should be configured in OkHttpEngine.")

    private val _incoming = Channel<Frame>()
    private val _closeReason = CompletableDeferred<CloseReason?>()

    override val incoming: ReceiveChannel<Frame>
        get() = _incoming

    override val closeReason: Deferred<CloseReason?>
        get() = _closeReason

    @OptIn(InternalAPI::class)
    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {
        require(negotiatedExtensions.isEmpty()) { "Extensions are not supported." }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    override val outgoing: SendChannel<Frame> = actor {
        val websocket: WebSocket = webSocketFactory.newWebSocket(engineRequest, self.await())
        var closeReason = DEFAULT_CLOSE_REASON_ERROR

        try {
            for (frame in channel) {
                when (frame) {
                    is Frame.Binary -> websocket.send(frame.data.toByteString(0, frame.data.size))
                    is Frame.Text -> websocket.send(String(frame.data))
                    is Frame.Close -> {
                        val outgoingCloseReason = frame.readReason()!!
                        if (!outgoingCloseReason.isReserved()) {
                            closeReason = outgoingCloseReason
                        }
                        return@actor
                    }

                    else -> throw UnsupportedFrameTypeException(frame)
                }
            }
        } finally {
            try {
                websocket.close(closeReason.code.toInt(), closeReason.message)
            } catch (cause: Throwable) {
                websocket.cancel()
                throw cause
            }
        }
    }

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        originResponse.complete(response)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        _incoming.trySendBlocking(Frame.Binary(true, bytes.toByteArray()))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        _incoming.trySendBlocking(Frame.Text(true, text.toByteArray()))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)

        _closeReason.complete(CloseReason(code.toShort(), reason))
        _incoming.close()
        outgoing.close(
            CancellationException(
                "WebSocket session closed with code ${CloseReason.Codes.byCode(code.toShort())?.toString() ?: code}."
            )
        )
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)

        _closeReason.complete(CloseReason(code.toShort(), reason))
        try {
            outgoing.trySendBlocking(Frame.Close(CloseReason(code.toShort(), reason)))
        } catch (ignore: Throwable) {
        }
        _incoming.close()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)

        _closeReason.completeExceptionally(t)
        originResponse.completeExceptionally(t)
        _incoming.close(t)
        outgoing.close(t)
    }

    override suspend fun flush() {
    }

    /**
     * Creates a new web socket and starts the session.
     */
    public fun start() {
        self.complete(this)
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        DeprecationLevel.ERROR
    )
    override fun terminate() {
        coroutineContext.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("KDocMissingDocumentation")
public class UnsupportedFrameTypeException(
    private val frame: Frame
) : IllegalArgumentException("Unsupported frame type: $frame"), CopyableThrowable<UnsupportedFrameTypeException> {
    override fun createCopy(): UnsupportedFrameTypeException = UnsupportedFrameTypeException(frame).also {
        it.initCause(this)
    }
}

@OptIn(InternalAPI::class)
@Suppress("DEPRECATION")
private fun CloseReason.isReserved() = CloseReason.Codes.byCode(code).let { recognized ->
    recognized == null || recognized == CloseReason.Codes.CLOSED_ABNORMALLY
}

private val DEFAULT_CLOSE_REASON_ERROR: CloseReason =
    CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Client failure")


