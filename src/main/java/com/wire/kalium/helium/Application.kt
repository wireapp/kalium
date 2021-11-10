package com.wire.kalium.helium

import com.wire.bots.cryptobox.CryptoException
import com.wire.helium.EventDecoder
import com.wire.helium.SocketReconnectHandler
import com.wire.helium.UserMessageResource
import com.wire.helium.models.NotificationList
import com.wire.kalium.MessageHandler
import com.wire.kalium.backend.models.NewBot
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.models.otr.PreKey
import com.wire.kalium.tools.Logger
import org.glassfish.tyrus.client.ClientManager
import org.glassfish.tyrus.client.ClientProperties
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.websocket.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.Cookie

@ClientEndpoint(decoders = EventDecoder::class)
class Application {
    private val renewal: ScheduledExecutorService
    private val email: String
    private val password: String
    private var sync: Boolean
    private var wsUrl: String?
    private var storageFactory: StorageFactory? = null
    private var cryptoFactory: CryptoFactory? = null
    private var client: Client? = null
    private var handler: MessageHandler? = null
    private var userMessageResource: UserMessageResource? = null
    var userId: UUID? = null
        private set
    private var session: Session? = null
    private var loginClient: LoginClient? = null
    private var cookie: Cookie? = null

    constructor(email: String, password: String, sync: Boolean, wsUrl: String?) {
        this.email = email
        this.password = password
        this.sync = sync
        this.wsUrl = wsUrl
        renewal = Executors.newScheduledThreadPool(1)
    }

    constructor(email: String, password: String) {
        this.email = email
        this.password = password
        sync = true
        wsUrl = "wss://prod-nginz-ssl.wire.com"
        renewal = Executors.newScheduledThreadPool(1)
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

    fun addClient(client: Client?): Application {
        this.client = client
        return this
    }

    fun addCryptoFactory(cryptoFactory: CryptoFactory?): Application {
        this.cryptoFactory = cryptoFactory
        return this
    }

    fun addStorageFactory(storageFactory: StorageFactory?): Application {
        this.storageFactory = storageFactory
        return this
    }

    @Throws(Exception::class)
    fun stop() {
        Logger.info("Logging out...")
        val state: NewBot = storageFactory.create(userId).getState()
        loginClient!!.logout(cookie, state.token)
    }

    @Throws(Exception::class)
    fun start() {
        loginClient = LoginClient(client)
        val access: com.wire.helium.models.Access = loginClient!!.login(email, password, true)
        userId = access.getUserId()
        cookie = convert(access.getCookie())
        var clientId = clientId
        if (clientId == null) {
            clientId = newDevice(userId, password, access.getAccessToken())
            Logger.info("Created new device. clientId: %s", clientId)
        }
        var state: NewBot = updateState(userId!!, clientId, access.getAccessToken(), null)
        Logger.info("Logged in as: %s, userId: %s, clientId: %s", email, state.id, state.client)
        val deviceId: String = state.client
        renewal.scheduleAtFixedRate({
            try {
                val newAccess: com.wire.helium.models.Access = loginClient!!.renewAccessToken(cookie)
                updateState(userId!!, deviceId, newAccess.getAccessToken(), null)
                if (newAccess.hasCookie()) {
                    cookie = convert(newAccess.getCookie())
                }
                Logger.info("Updated access token. Exp in: %d sec, cookie: %s",
                        newAccess.expiresIn,
                        newAccess.hasCookie())
            } catch (e: Exception) {
                Logger.exception("Token renewal error: %s", e, e.message)
            }
        }, access.expiresIn.toLong(), access.expiresIn.toLong(), TimeUnit.SECONDS)
        renewal.scheduleAtFixedRate({
            try {
                if (session != null) {
                    session.getBasicRemote().sendBinary(ByteBuffer.wrap("ping".toByteArray(StandardCharsets.UTF_8)))
                }
            } catch (e: Exception) {
                Logger.exception("Ping error: %s", e, e.message)
            }
        }, 10, 10, TimeUnit.SECONDS)
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
                    SIZE)
            while (!notificationList.notifications.isEmpty()) {
                for (notification in notificationList.notifications) {
                    onMessage(notification)
                    state = updateState(userId!!, state.client, state.token, notification.id)
                }
                notificationList = loginClient!!.retrieveNotifications(state.client, since(state), state.token, SIZE)
            }
        }
        if (wsUrl != null) {
            session = connectSocket(wsUrl)
            Logger.info("Websocket %s uri: %s", session.isOpen(), session.getRequestURI())
        }
    }

    private fun convert(cookie: Cookie): Cookie {
        return Cookie(cookie.name, cookie.value)
    }

    private fun since(state: NewBot): UUID? {
        return if (state.locale != null) UUID.fromString(state.locale) else null
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

    @Throws(CryptoException::class, HttpException::class)
    fun newDevice(userId: UUID?, password: String?, token: String?): String {
        val crypto: Crypto = cryptoFactory.create(userId)
        val loginClient = LoginClient(client)
        val preKeys: ArrayList<PreKey> = crypto.newPreKeys(0, 20)
        val lastKey: PreKey = crypto.newLastPreKey()
        return loginClient.registerClient(token!!, password!!, preKeys, lastKey)
    }

    val clientId: String?
        get() = try {
            storageFactory.create(userId).getState().client
        } catch (ex: IOException) {
            null
        }

    @Throws(CryptoException::class, IOException::class)
    fun getWireClient(conversationId: UUID?): WireClientImp {
        return userMessageResource!!.getWireClient(conversationId)
    }

    @Throws(IOException::class)
    fun updateState(userId: UUID, clientId: String, token: String, last: UUID?): NewBot {
        val state: State = storageFactory.create(userId)
        var newBot: NewBot
        try {
            newBot = state.getState()
        } catch (ex: IOException) {
            newBot = NewBot()
            newBot.id = userId
            newBot.client = clientId
        }
        newBot.token = token
        if (last != null) newBot.locale = last.toString()
        state.saveState(newBot)
        return state.getState()
    }

    companion object {
        private const val SIZE = 100
    }
}
