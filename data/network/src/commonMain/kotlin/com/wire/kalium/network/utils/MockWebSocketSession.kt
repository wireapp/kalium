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

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("EmptyFunctionBlock")
class MockWebSocketSession(
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : WebSocketSession {
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE

    private val _incoming = Channel<Frame>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<Frame> get() = _incoming
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)

    override val extensions: List<WebSocketExtension<*>> = emptyList()

    override suspend fun flush() {}

    override fun terminate() {}

    suspend fun emit(message: FakeWebSocketMessage) {
        val frame = when (message) {
            is FakeWebSocketMessage.Binary -> Frame.Binary(true, message.json.encodeToByteArray())
            is FakeWebSocketMessage.Text -> Frame.Text(message.text)
            is FakeWebSocketMessage.Raw -> message.frame
        }
        _incoming.send(frame)
    }
}

sealed class FakeWebSocketMessage {
    data class Binary(val json: String) : FakeWebSocketMessage()
    data class Text(val text: String) : FakeWebSocketMessage()
    data class Raw(val frame: Frame) : FakeWebSocketMessage()
}
