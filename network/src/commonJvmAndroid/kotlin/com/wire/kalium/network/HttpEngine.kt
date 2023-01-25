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
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.tools.isProxyRequired
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit

actual fun defaultHttpEngine(
    serverConfigDTOApiProxy: ServerConfigDTO.ApiProxy?,
    proxyCredentials: ProxyCredentialsDTO?
): HttpClientEngine = OkHttp.create {
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

        val client = OkHttpClient.Builder().pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS).proxy(proxy)
            .build()
        preconfigured = client
        webSocketFactory = KaliumWebSocketFactory(client)

    } else {
        val client = OkHttpClient.Builder().pingInterval(WEBSOCKET_PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS).build()
        preconfigured = client
        webSocketFactory = KaliumWebSocketFactory(client)
    }
}
