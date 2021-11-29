package com.wire.kalium

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.helium.EventDecoder
import com.wire.kalium.helium.SocketReconnectHandler
import com.wire.kalium.models.backend.Access
import com.wire.kalium.models.backend.Event
import com.wire.kalium.models.backend.NotificationList
import com.wire.kalium.models.outbound.otr.PreKey
import com.wire.kalium.tools.Logger
import org.glassfish.tyrus.client.ClientManager
import org.glassfish.tyrus.client.ClientProperties
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.websocket.ClientEndpoint
import javax.websocket.CloseReason
import javax.websocket.DeploymentException
import javax.websocket.EndpointConfig
import javax.websocket.OnClose
import javax.websocket.OnMessage
import javax.websocket.OnOpen
import javax.websocket.Session
import javax.ws.rs.client.Client

@ClientEndpoint(decoders = [EventDecoder::class])
class WebSocketApplication(val email: String, val password: String) {
    private val renewal: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    private var handler: MessageHandler? = null
    private var eventProcessor: EventProcessor? = null
    private var sync: Boolean = false
    private var wsUrl: String? = null
    private var session: Session? = null
    lateinit var loginClient: LoginClient
    private var access: Access? = null
    protected lateinit var client: Client
    protected var crypto: Crypto? = null
    private var last: UUID? = null
    protected var clientId: String? = null

    fun addWSUrl(wsUrl: String?): WebSocketApplication {
        this.wsUrl = wsUrl
        return this
    }

    fun shouldSync(sync: Boolean): WebSocketApplication {
        this.sync = sync
        return this
    }

    fun addHandler(handler: MessageHandler?): WebSocketApplication {
        this.handler = handler
        return this
    }

    fun addClient(client: Client): WebSocketApplication {
        this.client = client
        return this
    }

    fun addCrypto(crypto: Crypto?): WebSocketApplication {
        this.crypto = crypto
        return this
    }

    @Throws(Exception::class)
    fun start() {
        loginClient = LoginClient(client)
        access = loginClient.login(email, password)

        clientId = createNewDevice(password, access!!.access_token)

        eventProcessor = EventProcessor(handler!!)
            .addClient(client)
            .addCrypto(crypto!!)

        // Pull from notification stream
        if (sync) {
            var notificationList: NotificationList = loginClient.retrieveNotifications(
                clientId!!,
                last,
                access!!.access_token,
                100
            )
            while (!notificationList.notifications.isEmpty()) {
                for (notification in notificationList.notifications) {
                    onMessage(notification)
                    last = notification.id
                }
                notificationList = loginClient.retrieveNotifications(clientId!!, last, access!!.access_token, 100)
            }
        }

        // Socket Pinger
        renewal.scheduleAtFixedRate({
            try {
                if (session != null) {
                    session!!.basicRemote.sendBinary(ByteBuffer.wrap("ping".toByteArray(StandardCharsets.UTF_8)))
                }
            } catch (e: Exception) {
                Logger.exception(message = "Ping error: ${e.message}", throwable = e)
            }
        }, 60, 60, TimeUnit.SECONDS)

        // Access token renewal
        renewal.scheduleAtFixedRate({
            try {
                access = loginClient.renewAccessToken(access!!.cookie!!)

                Logger.info("Updated access token. Exp in: ${access!!.expires_in} sec, cookie: ${access!!.cookie}")
            } catch (e: Exception) {
                Logger.exception(message = "Token renewal error: ${e.message}", throwable = e)
            }
        }, access!!.expires_in.toLong(), access!!.expires_in.toLong(), TimeUnit.SECONDS)

        session = connectSocket(wsUrl)
        Logger.info("Websocket ${session!!.isOpen} uri: ${session!!.requestURI}")
    }

    @OnMessage
    fun onMessage(event: Event?) {
        if (event == null) return
        event.payload?.let { payloadArray ->
            for (payload in payloadArray) {
                val wireClient = createWireClient(payload.conversation)
                eventProcessor!!.processEvent(event.id!!, payload, wireClient)
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

        val wss: URI? = client
            .target(wsUrl)
            .path("await")
            .queryParam("client", clientId)
            .queryParam("access_token", access!!.access_token)
            .uri

        // connect the Websocket
        val container: ClientManager = ClientManager.createClient()
        container.properties[ClientProperties.RECONNECT_HANDLER] = SocketReconnectHandler(5)
        container.defaultMaxSessionIdleTimeout = -1
        return container.connectToServer(this, wss)
    }

    @Throws(CryptoException::class, HttpException::class)
    private fun createNewDevice(password: String, token: String): String? {
        val loginClient = LoginClient(client)
        val preKeys: ArrayList<PreKey> = crypto!!.newPreKeys(0, 20)
        val lastKey: PreKey = crypto!!.newLastPreKey()
        return loginClient.registerClient(token, password, preKeys, lastKey)
    }

    @Throws(CryptoException::class, IOException::class)
    fun createWireClient(conversationId: UUID?): WireClient {
        val api = API(client, conversationId, access!!.access_token)
        return WireClient(api, crypto!!, access!!, clientId!!)
    }
}
