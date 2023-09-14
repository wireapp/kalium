/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.tools.isProxyRequired
import com.wire.kalium.util.DelicateKaliumApi
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
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

private object OkHttpSingleton {
    private val sharedClient = OkHttpClient.Builder().apply {
        pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .connectTimeout(WEBSOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(WEBSOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(WEBSOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
    }.build()

    fun createNew(block: OkHttpClient.Builder.() -> Unit): OkHttpClient {
        return sharedClient.newBuilder().apply(block).build()
    }
}

actual fun defaultHttpEngine(
    serverConfigDTOApiProxy: ServerConfigDTO.ApiProxy?,
    proxyCredentials: ProxyCredentialsDTO?,
    ignoreSSLCertificates: Boolean,
    certificatePinning: CertificatePinning
): HttpClientEngine = OkHttp.create {
    OkHttpSingleton.createNew {
        if (certificatePinning.isNotEmpty()) {
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

        // OkHttp doesn't support configuring ping intervals dynamically,
        // so they must be set when creating the Engine
        // See https://youtrack.jetbrains.com/issue/KTOR-4752
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

    }.also {
        preconfigured = it
        webSocketFactory = KaliumWebSocketFactory(it)
    }
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
