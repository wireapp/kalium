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
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
) : WebSocketSession {
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE

    override val incoming: ReceiveChannel<Frame> = Channel(Channel.UNLIMITED)
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)

    override val extensions: List<WebSocketExtension<*>> = emptyList()

    override suspend fun flush() {}

    @Deprecated("Use cancel() instead.", replaceWith = ReplaceWith("cancel()", "kotlinx.coroutines.cancel"), level = DeprecationLevel.ERROR)
    @Suppress("DEPRECATION")
    override fun terminate() {}
}
