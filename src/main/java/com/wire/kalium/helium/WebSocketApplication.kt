package com.wire.kalium.helium

import com.wire.helium.EventDecoder
import com.wire.kalium.MessageHandler
import com.wire.kalium.backend.models.NewBot
import com.wire.kalium.helium.models.NotificationList
import com.wire.kalium.tools.Logger
import org.glassfish.tyrus.client.ClientManager
import org.glassfish.tyrus.client.ClientProperties
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import javax.websocket.*

@ClientEndpoint(decoders = EventDecoder::class)
class WebSocketApplication : Application {
    private var handler: MessageHandler? = null
    private var userMessageResource: UserMessageResource? = null
    private var sync: Boolean
    private var wsUrl: String?
    private var session: Session? = null

    constructor(email: String, password: String, sync: Boolean, wsUrl: String?) {
        :Application(email, password)
        this.sync = sync
        this.wsUrl = wsUrl
    }

    fun addWSUrl(wsUrl: String?): Application {
        this.wsUrl = wsUrl
        return this
    }

    fun shouldSync(sync: Boolean): Application {
        this.sync = sync
        return this
    }

    fun addHandler(handler: MessageHandler?): Application {
        this.handler = handler
        return this
    }

    @Throws(Exception::class)
    fun start() {
        : base.start()

        userMessageResource = UserMessageResource(handler)
                .addUserId(userId)
                .addClient(client)
                .addCryptoFactory(cryptoFactory)
                .addStorageFactory(storageFactory)

        // Pull from notification stream
        if (sync) {
            var notificationList: NotificationList = loginClient!!.retrieveNotifications(state.client,
                    since(state),
                    state.token,
                    Application.SIZE)
            while (!notificationList.notifications.isEmpty()) {
                for (notification in notificationList.notifications) {
                    onMessage(notification)
                    state = updateState(userId!!, state.client, state.token, notification.id)
                }
                notificationList = loginClient!!.retrieveNotifications(state.client, since(state), state.token, Application.SIZE)
            }
        }

        renewal.scheduleAtFixedRate({
            try {
                if (session != null) {
                    session.getBasicRemote().sendBinary(ByteBuffer.wrap("ping".toByteArray(StandardCharsets.UTF_8)))
                }
            } catch (e: Exception) {
                Logger.exception("Ping error: %s", e, e.message)
            }
        }, 10, 10, TimeUnit.SECONDS)

        session = connectSocket(wsUrl)
        Logger.info("Websocket %s uri: %s", session.isOpen(), session.getRequestURI())
    }

    @OnMessage
    fun onMessage(event: com.wire.helium.models.Event?) {
        if (event == null) return
        for (payload in event.payload) {
            try {
                when (payload.type) {
                    "team.member-join", "user.update" -> userMessageResource!!.onUpdate(event.id, payload)
                    "user.connection" -> userMessageResource!!.onNewMessage(
                            event.id,  /* payload.connection.from, */ //todo check this!!
                            payload.connection.convId,
                            payload)
                    "conversation.otr-message-add", "conversation.member-join", "conversation.member-leave", "conversation.create" -> userMessageResource!!.onNewMessage(
                            event.id,
                            payload.convId,
                            payload)
                    else -> Logger.info("Unknown type: %s, from: %s", payload.type, payload.from)
                }
            } catch (e: Exception) {
                Logger.exception("Endpoint:onMessage: %s %s", e, e.message, payload.type)
            }
        }
    }

    @OnOpen
    fun onOpen(session: Session, config: EndpointConfig?) {
        Logger.debug("Session opened: %s", session.getId())
    }

    @OnClose
    @Throws(IOException::class, DeploymentException::class)
    fun onClose(closed: Session, reason: CloseReason?) {
        Logger.debug("Session closed: %s, %s", closed.getId(), reason)
        session = connectSocket(wsUrl)
    }

    @Throws(IOException::class, DeploymentException::class)
    private fun connectSocket(wsUrl: String?): Session {
        val newBot: NewBot = storageFactory
                .create(userId)
                .getState()
        val wss: URI = client
                .target(wsUrl)
                .path("await")
                .queryParam("client", newBot.client)
                .queryParam("access_token", newBot.token)
                .getUri()

        // connect the Websocket
        val container: ClientManager = ClientManager.createClient()
        container.getProperties().put(ClientProperties.RECONNECT_HANDLER, SocketReconnectHandler(5))
        container.setDefaultMaxSessionIdleTimeout(-1)
        return container.connectToServer(this, wss)
    }

    private fun since(state: NewBot): UUID? {
        return if (state.locale != null) UUID.fromString(state.locale) else null
    }
}
