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
@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.logic.data.message.linkpreview

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.Url
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException

internal actual fun createPreviewHttpEngine(): HttpClientEngine = createOkHttpPreviewEngine()

internal fun createOkHttpPreviewEngine(dns: Dns = Dns.SYSTEM): HttpClientEngine = OkHttp.create {
    preconfigured = OkHttpClient.Builder()
        .dns(PublicPreviewDns(dns))
        // A proxy could resolve the hostname independently and reach a blocked destination.
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        // OkHttp bounds HTTP/1 response headers; its HTTP/2 HPACK reader has no total header limit.
        .protocols(listOf(Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            val request = chain.request()
            // OkHttp skips its DNS callback for literal IP addresses, so validate those here too.
            if (!isAllowedPreviewUrl(Url(request.url.toString()))) {
                throw IOException("Blocked link-preview URL")
            }
            chain.proceed(request)
        }
        .build()
}

/** Validates the exact DNS answers OkHttp will use to open its connection. */
internal class PublicPreviewDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { address ->
                address.hostAddress?.let(::isPublicPreviewAddress) != true
            }
        ) {
            throw UnknownHostException("Blocked link-preview destination")
        }

        // Keep the resolved addresses, rather than resolving a checked hostname a second time.
        // The original request hostname remains intact for TLS/SNI and certificate verification.
        return addresses
    }
}
