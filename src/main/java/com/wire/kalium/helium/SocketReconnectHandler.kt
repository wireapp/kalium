package com.wire.helium

import org.glassfish.tyrus.client.ClientManager
import javax.websocket.CloseReason

class SocketReconnectHandler(  // seconds
        private val delay: Int) : ClientManager.ReconnectHandler() {
    fun onDisconnect(closeReason: CloseReason?): Boolean {
        Logger.debug("Websocket onDisconnect: reason: %s", closeReason)
        return false
    }

    fun onConnectFailure(e: Exception): Boolean {
        Logger.exception("Websocket onConnectFailure: reason: %s", e, e.message)
        return true
    }

    fun getDelay(): Long {
        return delay.toLong()
    }
}
