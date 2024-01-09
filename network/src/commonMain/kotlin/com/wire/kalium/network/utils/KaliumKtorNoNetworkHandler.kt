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
package com.wire.kalium.network.utils

import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.util.AttributeKey

class KaliumKtorNoNetworkHandler private constructor(
    private val networkStateObserver: NetworkStateObserver
) {
    class Config {
        lateinit var networkStateObserver: NetworkStateObserver
    }

    private fun setupHandlingNoNetwork(client: HttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            networkStateObserver.observeNetworkState().value.let { networkState ->
                if (networkState is NetworkState.ConnectedWithInternet) {
                    proceedWith(subject)
                } else {
                    throw KaliumException.NoNetwork(networkState)
                }
            }
        }
    }

    companion object Plugin : HttpClientPlugin<Config, KaliumKtorNoNetworkHandler> {
        override val key: AttributeKey<KaliumKtorNoNetworkHandler> = AttributeKey("NoNetworkHandler")

        override fun prepare(block: Config.() -> Unit): KaliumKtorNoNetworkHandler {
            val config = Config().apply(block)
            return KaliumKtorNoNetworkHandler(config.networkStateObserver)
        }

        override fun install(plugin: KaliumKtorNoNetworkHandler, scope: HttpClient) {
            plugin.setupHandlingNoNetwork(scope)
        }
    }
}
