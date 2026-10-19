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

@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.network

import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.unbound.configuration.isProxyRequired
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.util.InternalKaliumApi
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.GzipSource
import okio.buffer
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val MAX_REQUESTS_PER_HOST = 15
private const val MAX_IDLE_CONNECTIONS = 15
private const val IDLE_CONNECTION_KEEP_ALIVE_TIME_MINUTES = 1L
private const val CONTENT_ENCODING_HEADER = "content-encoding"
private const val GZIP_ENCODING = "gzip"

actual fun defaultHttpEngine(
    serverConfigDTOApiProxy: ServerConfigDTO.ApiProxy?,
    proxyCredentials: ProxyCredentialsDTO?,
    ignoreSSLCertificates: Boolean,
    certificatePinning: CertificatePinning,
    httpTrafficObserver: HttpTrafficObserver?,
): HttpClientEngine = OkHttp.create {
    buildOkhttpClient {
        connectionSpecs(supportedConnectionSpecs())
        connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, IDLE_CONNECTION_KEEP_ALIVE_TIME_MINUTES, TimeUnit.MINUTES))
        dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })

        if (certificatePinning.isNotEmpty() && !ignoreSSLCertificates) {
            val certPinner = CertificatePinner.Builder().apply {
                certificatePinning.forEach { (cert, hosts) ->
                    hosts.forEach { host ->
                        add(host, cert)
                    }
                }
            }.build()
            certificatePinner(certPinner)
        }

        if (ignoreSSLCertificates) ignoreAllSSLErrors()

        if (isProxyRequired(serverConfigDTOApiProxy)) {
            if (serverConfigDTOApiProxy?.needsAuthentication == true) {
                if (proxyCredentials == null) error("Credentials does not exist")
                with(proxyCredentials) {
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication {
                            return PasswordAuthentication(username, password?.toCharArray())
                        }
                    })
                }
            }

            val proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved(serverConfigDTOApiProxy?.host, serverConfigDTOApiProxy!!.port)
            )

            proxy(proxy)
        }

        if (httpTrafficObserver != null) {
            addNetworkInterceptor(httpTrafficObserverInterceptor(httpTrafficObserver))
        }

    }.also {
        preconfigured = it
        webSocketFactory = KaliumWebSocketFactory(it)
    }
}

/**
 * A network interceptor (added via [OkHttpClient.Builder.addNetworkInterceptor]) that hands the
 * fully-prepared request and raw response - including bodies - to [observer], then forwards them
 * unmodified (aside from re-wrapping the response body, which OkHttp requires once its bytes have
 * been read for inspection).
 */
@OptIn(InternalKaliumApi::class)
internal fun httpTrafficObserverInterceptor(observer: HttpTrafficObserver): Interceptor = Interceptor { chain ->
    val request = chain.request()

    var requestReported = false
    fun reportRequest(body: ByteArray?) {
        requestReported = true
        observer.onRequest(request.method, request.url.toString(), request.headers.toMultimap(), body)
    }

    val body = request.body
    val observedRequest = if (body == null || body.isDuplex()) {
        // Duplex writes can continue after the response arrives. Do not buffer or delay them.
        reportRequest(null)
        request
    } else {
        request.newBuilder().method(
            request.method,
            object : RequestBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun isOneShot() = body.isOneShot()

                override fun writeTo(sink: BufferedSink) {
                    val captured = Buffer()
                    val forwardingSink = object : ForwardingSink(sink) {
                        override fun write(source: Buffer, byteCount: Long) {
                            source.copyTo(captured, 0, byteCount)
                            super.write(source, byteCount)
                        }
                    }.buffer()
                    // Capture the actual send. Reading first would consume Ktor's one-shot uploads.
                    body.writeTo(forwardingSink)
                    forwardingSink.flush()
                    reportRequest(captured.readByteArray())
                }
            }
        ).build()
    }

    val response = try {
        chain.proceed(observedRequest)
    } finally {
        // An early final response to Expect: 100-continue can skip writeTo entirely.
        // Failed sends also need request metadata without consuming the upload body.
        if (!requestReported) reportRequest(null)
    }
    val (responseBytes, observableResponse) = readAndRewrapBody(response, response.body)

    observer.onResponse(
        method = request.method,
        url = request.url.toString(),
        statusCode = observableResponse.code,
        headers = observableResponse.headers.toMultimap(),
        body = responseBytes,
    )

    observableResponse
}

private fun readAndRewrapBody(response: Response, body: okhttp3.ResponseBody): Pair<ByteArray, Response> {
    val contentEncoding = response.header(CONTENT_ENCODING_HEADER)
    val isGzip = GZIP_ENCODING.equals(contentEncoding, ignoreCase = true)
    val bytes = body.use {
        if (body.contentLength() == 0L) {
            ByteArray(0)
        } else if (isGzip) {
            GzipSource(body.source()).buffer().use { it.readByteArray() }
        } else {
            body.bytes()
        }
    }
    val responseBuilder = response.newBuilder().body(bytes.toResponseBody(body.contentType()))
    if (isGzip) {
        // The body handed to downstream callers below is already decoded, so the
        // now-stale Content-Encoding/Content-Length headers must be dropped - otherwise
        // callers further up the chain (e.g. Ktor's ContentEncoding plugin) try to
        // gzip-decode the already-decoded bytes a second time and fail/corrupt the body.
        responseBuilder.removeHeader(CONTENT_ENCODING_HEADER)
        responseBuilder.removeHeader("Content-Length")
    }
    val rewrapped = responseBuilder.build()
    return bytes to rewrapped
}

private fun OkHttpClient.Builder.ignoreAllSSLErrors() {
    val naiveTrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
    }

    val insecureSocketFactory = SSLContext.getInstance("SSL").apply {
        val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
        init(null, trustAllCerts, SecureRandom())
    }.socketFactory

    sslSocketFactory(insecureSocketFactory, naiveTrustManager)
    hostnameVerifier { _, _ -> true }
}

fun supportedConnectionSpecs(): List<ConnectionSpec> {
    val wireSpec = ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS).build()
    return listOf(wireSpec)
}
