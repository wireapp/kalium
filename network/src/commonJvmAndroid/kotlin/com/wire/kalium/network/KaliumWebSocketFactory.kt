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

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Upon request, creates normal [WebSocket], but wraps the listener
 * to add logging.
 * @see WrapperListener
 */
class KaliumWebSocketFactory(private val okHttpClient: OkHttpClient) : WebSocket.Factory {

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        val wrapperListener = WrapperListener(listener)
        return okHttpClient.newWebSocket(request, wrapperListener)
    }

    /**
     * Wraps the provided [wrappedListener], keeping the normal behaviour,
     * but using [kaliumUtilLogger] to log all operations.
     */
    inner class WrapperListener(private val wrappedListener: WebSocketListener) : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            kaliumLogger.v("WEBSOCKET: onClosed($code, $reason)")
            wrappedListener.onClosed(webSocket, code, reason)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            kaliumLogger.v("WEBSOCKET: onClosing($code, $reason)")
            wrappedListener.onClosing(webSocket, code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            kaliumLogger.v(
                "WEBSOCKET: onFailure($t, protocol:${response?.protocol} " +
                        "                        code:${response?.code} message:${response?.message})"
            )
            wrappedListener.onFailure(webSocket, t, response)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            kaliumLogger.v("WEBSOCKET: onMessage()")
            wrappedListener.onMessage(webSocket, text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            super.onMessage(webSocket, bytes)
            kaliumLogger.v("WEBSOCKET: onMessage()")
            wrappedListener.onMessage(webSocket, bytes)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            kaliumLogger.v(
                "WEBSOCKET: onOpen(protocol:${response.protocol} " +
                        "code:${response.code} message:${response.message} "
            )
            wrappedListener.onOpen(webSocket, response)
        }
    }
}
