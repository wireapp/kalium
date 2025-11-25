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

import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.session.CertificatePinning
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.certificates.CertificatePinner

private const val SOCKS_PROXY_ENABLE_KEY = "SOCKSEnable"
private const val SOCKS_PROXY_HOST_KEY = "SOCKSProxy"
private const val SOCKS_PROXY_PORT_KEY = "SOCKSPort"
private const val SOCKS_PROXY_USER_KEY = "SOCKSUser"
private const val SOCKS_PROXY_PASSWORD_KEY = "SOCKSPassword"

actual fun defaultHttpEngine(
    serverConfigDTOApiProxy: ServerConfigDTO.ApiProxy?,
    proxyCredentials: ProxyCredentialsDTO?,
    ignoreSSLCertificates: Boolean,
    certificatePinning: CertificatePinning
): HttpClientEngine {
    return Darwin.create {
        pipelining = true

        if (serverConfigDTOApiProxy != null) {
            configureSession {
                val proxyDict = mutableMapOf<Any?, Any?>(
                    SOCKS_PROXY_ENABLE_KEY to 1,
                    SOCKS_PROXY_HOST_KEY to serverConfigDTOApiProxy.host,
                    SOCKS_PROXY_PORT_KEY to serverConfigDTOApiProxy.port
                )

                if (serverConfigDTOApiProxy.needsAuthentication && proxyCredentials != null) {
                    proxyCredentials.username?.let { username ->
                        proxyDict[SOCKS_PROXY_USER_KEY] = username
                    }
                    proxyCredentials.password?.let { password ->
                        proxyDict[SOCKS_PROXY_PASSWORD_KEY] = password
                    }
                }

                connectionProxyDictionary = proxyDict
            }
        }

        if (certificatePinning.isNotEmpty()) {
            val certPinner: CertificatePinner = CertificatePinner.Builder().apply {
                certificatePinning.forEach { (cert, hosts) ->
                    hosts.forEach { host ->
                        add(host, cert)
                    }
                }
            }.build()
            handleChallenge(certPinner)
        }
    }
}

actual fun clearTextTrafficEngine(): HttpClientEngine = Darwin.create()
