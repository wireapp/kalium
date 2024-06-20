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

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.base.unbound.configuration.isProxyRequired
import com.wire.kalium.network.session.CertificatePinning
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

actual fun defaultHttpEngine(
    serverConfigDTOApiProxy: ServerConfigDTO.ApiProxy?,
    proxyCredentials: ProxyCredentialsDTO?,
    ignoreSSLCertificates: Boolean,
    certificatePinning: CertificatePinning
): HttpClientEngine = OkHttp.create {
    buildOkhttpClient {
        connectionSpecs(supportedConnectionSpecs())

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

fun supportedConnectionSpecs(): List<ConnectionSpec> {
    val wireSpec = ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS).build()
    return listOf(wireSpec)
}

actual fun clearTextTrafficEngine(): HttpClientEngine = OkHttp.create {
    buildClearTextTrafficOkhttpClient()
}
