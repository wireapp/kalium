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
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSource
import okio.buffer
import java.io.IOException
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
internal fun httpTrafficObserverInterceptor(observer: HttpTrafficObserver): Interceptor = Interceptor { chain ->
    val request = chain.request()

    val requestBody = request.body?.let { body ->
        try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readByteArray()
        } catch (e: IOException) {
            null
        }
    }
    observer.onRequest(
        method = request.method,
        url = request.url.toString(),
        headers = request.headers.toMultimap(),
        body = requestBody,
    )

    val response = chain.proceed(request)
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
    val bytes = if (isGzip) {
        if (body.contentLength() != 0L) GzipSource(body.source()).buffer().readByteArray() else ByteArray(0)
    } else {
        if (body.contentLength() != 0L) body.bytes() else ByteArray(0)
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
