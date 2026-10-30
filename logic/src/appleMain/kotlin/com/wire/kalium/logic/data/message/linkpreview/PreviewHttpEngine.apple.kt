/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, io.ktor.utils.io.InternalAPI::class)

package com.wire.kalium.logic.data.message.linkpreview

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseResponse
import io.ktor.http.fullPath
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import platform.Network.NW_PARAMETERS_DEFAULT_CONFIGURATION
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_create
import platform.Network.nw_connection_receive
import platform.Network.nw_connection_send
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_connection_state_cancelled
import platform.Network.nw_connection_state_failed
import platform.Network.nw_connection_state_ready
import platform.Network.nw_connection_t
import platform.Network.nw_content_context_create
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_endpoint_get_type
import platform.Network.nw_endpoint_type_address
import platform.Network.nw_error_get_error_code
import platform.Network.nw_error_t
import platform.Network.nw_parameters_create_secure_tcp
import platform.Network.nw_parameters_set_prefer_no_proxy
import platform.Network.nw_tls_copy_sec_protocol_options
import platform.Security.sec_protocol_options_add_tls_application_protocol
import platform.Security.sec_protocol_options_set_min_tls_protocol_version
import platform.Security.sec_protocol_options_set_tls_server_name
import platform.Security.tls_protocol_version_TLSv12
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_data_create_map
import platform.darwin.dispatch_data_get_size
import platform.darwin.dispatch_data_t
import platform.darwin.dispatch_queue_create
import platform.posix.size_tVar
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual fun createPreviewHttpEngine(): HttpClientEngine = ApplePreviewHttpEngine()

/**
 * A preview-only HTTP/1.1 engine. URLSession cannot bind its connection to a validated DNS answer,
 * so Network.framework opens a numeric endpoint while Apple's TLS stack verifies the original host.
 */
internal class ApplePreviewHttpEngine(
    private val resolveAddresses: suspend (String) -> List<String> = ::resolvePreviewHostAddresses,
    private val connect: suspend (host: String, address: String, port: Int) -> PreviewAppleConnection =
        { host, address, port -> NetworkPreviewConnection.open(host, address, port) }
) : HttpClientEngineBase("wire-preview-network") {
    override val config: HttpClientEngineConfig = HttpClientEngineConfig()
    override val supportedCapabilities = setOf(HttpTimeoutCapability)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        require(data.method == HttpMethod.Get && isAllowedPreviewUrl(data.url)) { "Unsupported preview request" }
        val requestTime = GMTDate()
        val context = callContext()
        val addresses = resolveAddresses(data.url.host.removeSurrounding("[", "]"))
        require(addresses.isNotEmpty() && addresses.all(::isPublicPreviewAddress)) { "Blocked preview destination" }

        val timeout = data.getCapabilityOrNull(HttpTimeoutCapability)
        val connection = connectToPublicAddress(data.url, addresses, timeout?.connectTimeoutMillis ?: CONNECT_TIMEOUT_MILLIS)
        val cancellation = context.job.invokeOnCompletion { connection.close() }
        try {
            connection.write(buildPreviewRequest(data))
            val response = readPreviewResponseHeaders(connection)
            val body = streamPreviewBody(connection, response, context, timeout?.socketTimeoutMillis ?: SOCKET_TIMEOUT_MILLIS)
            return HttpResponseData(response.status, requestTime, response.headers, response.version, body, context)
        } catch (cause: Throwable) {
            cancellation.dispose()
            connection.close()
            throw cause
        }
    }

    // Rethrow cancellation after closing any socket whose ownership was not transferred.
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun connectToPublicAddress(url: Url, addresses: List<String>, timeout: Long): PreviewAppleConnection {
        // Keep ownership outside withTimeout: cancellation can discard its return value even after
        // the socket became ready. Such a connection must still be closed.
        val opened = mutableListOf<PreviewAppleConnection>()
        return try {
            withTimeout(timeout) {
                // Try IPv4 before another IPv6 address if the first IPv6 route is unavailable (and vice versa).
                val firstFamily = addresses.filter { (':' in it) == (':' in addresses.first()) }
                val secondFamily = addresses.filter { (':' in it) != (':' in addresses.first()) }
                val ordered = (0 until maxOf(firstFamily.size, secondFamily.size)).flatMap { index ->
                    listOfNotNull(firstFamily.getOrNull(index), secondFamily.getOrNull(index))
                }
                val attemptTimeout = maxOf(MIN_CONNECTION_ATTEMPT_MILLIS, timeout / minOf(ordered.size, MAX_ATTEMPT_TIMEOUT_DIVISOR))
                var failure: Exception? = null
                for (address in ordered) {
                    try {
                        val result = withTimeoutOrNull(attemptTimeout) {
                            connect(url.host.removeSurrounding("[", "]"), address, url.port).also(opened::add)
                        }
                        if (result != null) return@withTimeout result
                        opened.forEach(PreviewAppleConnection::close)
                        opened.clear()
                    } catch (cause: CancellationException) {
                        throw cause
                    } catch (cause: Exception) {
                        failure = cause
                    }
                }
                throw IllegalStateException("No public preview address could be reached", failure)
            }
        } catch (cause: Throwable) {
            opened.forEach(PreviewAppleConnection::close)
            throw cause
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private fun streamPreviewBody(
    connection: PreviewAppleConnection,
    response: PreviewAppleResponseHead,
    context: CoroutineContext,
    socketTimeout: Long
): ByteReadChannel {
    val rawBody = ByteChannel(autoFlush = true)
    val body = ByteChannel(autoFlush = true)
    val scope = CoroutineScope(context)
    scope.launch {
        try {
            rawBody.writeFully(response.remainingBytes)
            while (true) {
                val bytes = withTimeout(socketTimeout) { connection.read() } ?: break
                rawBody.writeFully(bytes)
            }
            rawBody.close()
        } catch (cause: Throwable) {
            rawBody.cancel(cause)
        } finally {
            connection.close()
        }
    }
    scope.launch {
        try {
            if (response.status != HttpStatusCode.NoContent && response.status != HttpStatusCode.NotModified) {
                parseHttpBody(
                    version = response.version,
                    contentLength = response.headers[HttpHeaders.ContentLength]?.toLong() ?: -1,
                    transferEncoding = response.headers[HttpHeaders.TransferEncoding],
                    connectionOptions = ConnectionOptions.parse(response.headers[HttpHeaders.Connection] ?: "close"),
                    input = rawBody,
                    out = body
                )
            }
            body.close()
        } catch (cause: Throwable) {
            body.cancel(cause)
        } finally {
            rawBody.cancel(null)
            connection.close()
        }
    }
    return body
}

/** The injectable TLS byte stream keeps address-policy and HTTP framing tests independent of the network. */
internal interface PreviewAppleConnection {
    suspend fun write(bytes: ByteArray)
    suspend fun read(): ByteArray?
    fun close()
}

internal fun buildPreviewRequest(data: HttpRequestData): ByteArray {
    require(isAllowedPreviewUrl(data.url)) { "Unsupported preview URL" }
    val host = data.url.host.removeSurrounding("[", "]")
    val authority = (if (':' in host) "[$host]" else host) +
        if (data.url.port == data.url.protocol.defaultPort) "" else ":${data.url.port}"
    val path = data.url.fullPath.let { if (it.startsWith('/')) it else "/$it" }
    val userAgent = data.headers[HttpHeaders.UserAgent] ?: "Wire LinkPreviewFetcher"
    require(userAgent.none { it == '\r' || it == '\n' }) { "Invalid user agent" }
    return buildString {
        append("GET $path HTTP/1.1\r\n")
        append("Host: $authority\r\n")
        append("User-Agent: $userAgent\r\n")
        append("Accept-Encoding: identity\r\nConnection: close\r\n\r\n")
    }.encodeToByteArray()
}

internal data class PreviewAppleResponseHead(
    val status: HttpStatusCode,
    val headers: Headers,
    val version: HttpProtocolVersion,
    val remainingBytes: ByteArray
)

internal suspend fun readPreviewResponseHeaders(connection: PreviewAppleConnection): PreviewAppleResponseHead {
    var pending = ByteArray(0)
    repeat(MAX_INFORMATIONAL_RESPONSES + 1) {
        val buffer = Buffer().write(pending)
        var end = buffer.indexOf(HEADER_END)
        while (end < 0) {
            check(buffer.size <= MAX_HEADER_BYTES) { "Preview response headers are too large" }
            buffer.write(connection.read() ?: error("Incomplete preview response headers"))
            end = buffer.indexOf(HEADER_END)
        }
        check(end + HEADER_END.size <= MAX_HEADER_BYTES) { "Preview response headers are too large" }
        val parsed = parseResponse(ByteReadChannel(buffer.readByteArray(end + HEADER_END.size)))
            ?: error("Missing preview response")
        try {
            pending = buffer.readByteArray()
            if (parsed.status in HttpStatusCode.Continue.value until HttpStatusCode.OK.value) {
                check(parsed.status != HttpStatusCode.SwitchingProtocols.value) { "Preview protocol upgrades are not supported" }
            } else {
                val headers = Headers.build {
                    parsed.headers.offsets().forEach { offset ->
                        append(parsed.headers.nameAtOffset(offset).toString(), parsed.headers.valueAtOffset(offset).toString())
                    }
                }
                check(headers[HttpHeaders.ContentEncoding].let { it == null || it.equals("identity", ignoreCase = true) }) {
                    "Unsupported preview content encoding"
                }
                return PreviewAppleResponseHead(
                    HttpStatusCode(parsed.status, parsed.statusText.toString()),
                    headers,
                    HttpProtocolVersion.parse(parsed.version.toString()),
                    pending
                )
            }
        } finally {
            parsed.release()
        }
    }
    error("Too many informational preview responses")
}

private class NetworkPreviewConnection private constructor(private val connection: nw_connection_t) : PreviewAppleConnection {
    private val messageContext = checkNotNull(nw_content_context_create("wire-link-preview-request"))

    override suspend fun write(bytes: ByteArray): Unit = suspendCancellableCoroutine { continuation ->
        val data = bytes.usePinned { pinned ->
            // A null (default) destructor makes Dispatch copy the bytes before this pin is released.
            dispatch_data_create(pinned.addressOf(0), bytes.size.toULong(), null, null)
        }
        continuation.invokeOnCancellation { close() }
        nw_connection_send(connection, data, messageContext, true) { error ->
            if (error == null) continuation.resume(Unit) else continuation.resumeWithException(networkError(error))
        }
    }

    override suspend fun read(): ByteArray? = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        nw_connection_receive(connection, 1u, READ_BUFFER_SIZE.toUInt()) { data, _, complete, error ->
            when {
                error != null -> continuation.resumeWithException(networkError(error))
                data != null && dispatch_data_get_size(data) > 0u -> continuation.resume(data.copyBytes())
                complete -> continuation.resume(null)
                else -> continuation.resume(ByteArray(0))
            }
        }
    }

    override fun close() {
        nw_connection_set_state_changed_handler(connection, null)
        nw_connection_cancel(connection)
    }

    private suspend fun start() = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        nw_connection_set_state_changed_handler(connection) { state, error ->
            if (continuation.isActive) {
                when (state) {
                    nw_connection_state_ready -> {
                        nw_connection_set_state_changed_handler(connection, null)
                        continuation.resume(Unit)
                    }
                    nw_connection_state_failed -> {
                        nw_connection_set_state_changed_handler(connection, null)
                        continuation.resumeWithException(networkError(error))
                    }
                    nw_connection_state_cancelled -> continuation.cancel()
                }
            }
        }
        nw_connection_set_queue(connection, networkQueue)
        nw_connection_start(connection)
    }

    companion object {
        @Suppress("TooGenericExceptionCaught")
        suspend fun open(host: String, address: String, port: Int): PreviewAppleConnection {
            require(isPublicPreviewAddress(address)) { "Blocked preview destination" }
            val endpoint = checkNotNull(nw_endpoint_create_host(address, port.toString()))
            check(nw_endpoint_get_type(endpoint) == nw_endpoint_type_address) { "Preview endpoint must be numeric" }
            val parameters = checkNotNull(
                nw_parameters_create_secure_tcp(
                    configure_tls = { tls ->
                        val options = checkNotNull(nw_tls_copy_sec_protocol_options(tls))
                        // This name controls both SNI and Apple's default peer certificate verification.
                        sec_protocol_options_set_tls_server_name(options, host)
                        sec_protocol_options_set_min_tls_protocol_version(options, tls_protocol_version_TLSv12)
                        sec_protocol_options_add_tls_application_protocol(options, "http/1.1")
                    },
                    configure_tcp = NW_PARAMETERS_DEFAULT_CONFIGURATION
                )
            )
            nw_parameters_set_prefer_no_proxy(parameters, true)
            val connection = NetworkPreviewConnection(checkNotNull(nw_connection_create(endpoint, parameters)))
            try {
                connection.start()
                return connection
            } catch (cause: Throwable) {
                connection.close()
                throw cause
            }
        }
    }
}

private fun dispatch_data_t.copyBytes(): ByteArray = memScoped {
    val pointer = alloc<COpaquePointerVar>()
    val size = alloc<size_tVar>()
    val mapped = checkNotNull(dispatch_data_create_map(this@copyBytes, pointer.ptr, size.ptr))
    check(dispatch_data_get_size(mapped) <= READ_BUFFER_SIZE.toULong()) { "Preview network read is too large" }
    val bytes = checkNotNull(pointer.value).reinterpret<ByteVar>().readBytes(size.value.toInt())
    check(dispatch_data_get_size(mapped) == bytes.size.toULong())
    bytes
}

private fun networkError(error: nw_error_t): IllegalStateException =
    IllegalStateException("Preview connection failed (${error?.let(::nw_error_get_error_code) ?: 0})")

private val networkQueue = dispatch_queue_create("com.wire.link-preview", null)
private val HEADER_END = "\r\n\r\n".encodeUtf8()
private const val MAX_HEADER_BYTES = 64L * 1024
private const val MAX_INFORMATIONAL_RESPONSES = 5
private const val READ_BUFFER_SIZE = 8 * 1024
private const val CONNECT_TIMEOUT_MILLIS = 3_000L
private const val SOCKET_TIMEOUT_MILLIS = 5_000L
private const val MIN_CONNECTION_ATTEMPT_MILLIS = 1_000L
private const val MAX_ATTEMPT_TIMEOUT_DIVISOR = 3
