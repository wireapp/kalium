package com.wire.kalium.network

import io.ktor.client.HttpClient

fun interface WebSocketClientProvider {
    fun provideWebSocketClient(): HttpClient
}
