//package com.wire.kalium.helium
//
//import com.wire.kalium.MessageHandler
//import com.wire.kalium.backend.models.NewBot
//import com.wire.kalium.backend.models.Event
//import com.wire.kalium.backend.models.NotificationList
//import com.wire.kalium.tools.Logger
//import org.glassfish.tyrus.client.ClientManager
//import org.glassfish.tyrus.client.ClientProperties
//import java.io.IOException
//import java.net.URI
//import java.nio.ByteBuffer
//import java.nio.charset.StandardCharsets
//import java.util.*
//import java.util.concurrent.TimeUnit
//import javax.websocket.*
//
//@ClientEndpoint(decoders = [EventDecoder::class])
//class WebSocketApplication : Application {
//    private var handler: MessageHandler? = null
//    private var userMessageResource: UserMessageResource? = null
//    private var sync: Boolean
//    private var wsUrl: String?
//    private var session: Session? = null
//
//    constructor(email: String, password: String, sync: Boolean, wsUrl: String?) : super(email = email, password = password) {
//        this.sync = sync
//        this.wsUrl = wsUrl
//    }
//
//    fun addWSUrl(wsUrl: String?): Application {
//        this.wsUrl = wsUrl
//        return this
//    }
//
//    fun shouldSync(sync: Boolean): Application {
//        this.sync = sync
//        return this
//    }
//
//    fun addHandler(handler: MessageHandler?): Application {
//        this.handler = handler
//        return this
//    }
//
//    @Throws(Exception::class)
//    fun start() {
//        : base.start()
//
//        userMessageResource = UserMessageResource(handler)
//                .addUserId(userId)
//                .addClient(client)
//                .addCryptoFactory(cryptoFactory)
//                .addStorageFactory(storageFactory)
//
//        // Pull from notification stream
//        if (sync) {
//            var notificationList: NotificationList = loginClient!!.retrieveNotifications(state.client,
//                    since(state),
//                    state.token,
//                    Application.SIZE)
//            while (!notificationList.notifications.isEmpty()) {
//                for (notification in notificationList.notifications) {
//                    onMessage(notification)
//                    state = updateState(userId!!, state.client, state.token, notification.id)
//                }
//                notificationList = loginClient!!.retrieveNotifications(state.client, since(state), state.token, Application.SIZE)
//            }
//        }
//
//        renewal.scheduleAtFixedRate({
//            try {
//                if (session != null) {
//                    session!!.basicRemote.sendBinary(ByteBuffer.wrap("ping".toByteArray(StandardCharsets.UTF_8)))
//                }
//            } catch (e: Exception) {
//                Logger.exception(message = "Ping error: ${e.message}", throwable = e)
//            }
//        }, 10, 10, TimeUnit.SECONDS)
//
//        session = connectSocket(wsUrl)
//        Logger.info("Websocket ${session!!.isOpen} uri: ${session!!.requestURI}")
//    }
//
//    @OnMessage
//    fun onMessage(event: Event?) {
//        if (event == null) return
//        event.payload?.let { payloadArray ->
//            for (payload in payloadArray) {
//                try {
//                    when (payload.type) {
//                        "team.member-join", "user.update" -> userMessageResource!!.onUpdate(event.id, payload)
//                        "user.connection" -> userMessageResource!!.onNewMessage(
//                                event.id,  /* payload.connection.from, */ //todo check this!!
//                                payload.connection.conversation,
//                                payload)
//                        "conversation.otr-message-add", "conversation.member-join", "conversation.member-leave", "conversation.create" -> userMessageResource!!.onNewMessage(
//                                event.id,
//                                payload.conversation,
//                                payload)
//                        else -> Logger.info("Unknown type: %s, from: %s", payload.type, payload.from)
//                    }
//                } catch (e: Exception) {
//                    Logger.exception(message = "Endpoint:onMessage: ${e.message} ${payload.type}", throwable = e)
//                }
//            }
//        }
//    }
//
//    @OnOpen
//    fun onOpen(session: Session, config: EndpointConfig?) {
//        Logger.debug("Session opened: %s", session.getId())
//    }
//
//    @OnClose
//    @Throws(IOException::class, DeploymentException::class)
//    fun onClose(closed: Session, reason: CloseReason?) {
//        Logger.debug("Session closed: %s, %s", closed.getId(), reason)
//        session = connectSocket(wsUrl)
//    }
//
//    @Throws(IOException::class, DeploymentException::class)
//    private fun connectSocket(wsUrl: String?): Session {
//        val newBot: NewBot = storageFactory
//                .create(userId)
//                .getState()
//        val wss: URI? = client
//                ?.target(wsUrl)
//                ?.path("await")
//                ?.queryParam("client", newBot.client)
//                ?.queryParam("access_token", newBot.token)
//                ?.uri
//
//        // connect the Websocket
//        val container: ClientManager = ClientManager.createClient()
//        container.properties[ClientProperties.RECONNECT_HANDLER] = SocketReconnectHandler(5)
//        container.defaultMaxSessionIdleTimeout = -1
//        return container.connectToServer(this, wss)
//    }
//
//    private fun since(state: NewBot): UUID {
//        return UUID.fromString(state.locale)
//    }
//}
