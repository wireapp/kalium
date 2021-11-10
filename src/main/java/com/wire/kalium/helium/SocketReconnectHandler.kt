package com.wire.kalium.helium

import com.wire.kalium.tools.Logger
import org.glassfish.tyrus.client.ClientManager
import javax.websocket.CloseReason

class SocketReconnectHandler(  // seconds
        private val delay: Int) : ClientManager.ReconnectHandler() {
    override fun onDisconnect(closeReason: CloseReason?): Boolean {
        Logger.debug("Websocket onDisconnect: reason: %s", closeReason)
        return false
    }

    override fun onConnectFailure(e: Exception): Boolean {
        Logger.exception(message = "Websocket onConnectFailure: reason: ${e.message}", throwable = e)
        return true
    }

    override fun getDelay(): Long {
        return delay.toLong()
    }
}
