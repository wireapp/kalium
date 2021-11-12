package com.wire.kalium.helium

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.IState
import com.wire.kalium.backend.models.Access
import com.wire.kalium.backend.models.NewBot
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.models.otr.PreKey
import com.wire.kalium.tools.Logger
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import javax.ws.rs.client.Client
import javax.ws.rs.core.Cookie

class Application(protected val email: String, protected val password: String) {

    protected val renewal: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    protected var storage: IState? = null
    protected var crypto: Crypto? = null
    protected lateinit var client: Client
    protected var userId: UUID? = null
        private set
    lateinit var loginClient: LoginClient
    private lateinit var cookie: Cookie

    fun addClient(client: Client): Application {
        this.client = client
        return this
    }

    fun addCrypto(crypto: Crypto?): Application {
        this.crypto = crypto
        return this
    }

    fun addStorage(storage: IState?): Application {
        this.storage = storage
        return this
    }

    @Throws(Exception::class)
    fun stop() {
        Logger.info("Logging out...")
        val state: NewBot? = storage!!.getState()
        loginClient.logout(cookie, state!!.token!!)
    }

    @Throws(Exception::class)
    fun start() {
        loginClient = LoginClient(client)
        val access: Access = loginClient.login(email, password)
        userId = access.user
        // FIXME: converting cookie into cookie
        //cookie = convert(access.cookie.toJavaxCookie())

        var clientId = clientId
        if (clientId == null) {
            clientId = newDevice(userId!!, password, access.access_token)
            Logger.info("Created new device. clientId: %s", clientId)
        }
        val state: NewBot? = updateState(userId!!, clientId!!, access.access_token, null)
        Logger.info("Logged in as: %s, userId: %s, clientId: %s", email, state!!.id, state.client)
        val deviceId: String = state.client!!

//        renewal.scheduleAtFixedRate({
//            try {
//                val newAccess: Access = loginClient.renewAccessToken(cookie)
//                updateState(userId!!, deviceId, newAccess.access_token, null)
//                // FIXME: converting cookie into cookie
//                cookie = convert(newAccess.cookie.toJavaxCookie())
//                Logger.info("Updated access token. Exp in: ${newAccess.expires_in} sec, cookie: ${newAccess.cookie}")
//            } catch (e: Exception) {
//                Logger.exception(message = "Token renewal error: ${e.message}", throwable = e)
//            }
//        }, access.expires_in.toLong(), access.expires_in.toLong(), TimeUnit.SECONDS)
    }

    // TODO: why to convert a javax Cookie into javax Cookie
    private fun convert(cookie: Cookie): Cookie {
        return Cookie(cookie.name, cookie.value)
    }

    @Throws(CryptoException::class, HttpException::class)
    fun newDevice(userId: UUID, password: String, token: String): String? {
        val loginClient = LoginClient(client)
        val preKeys: ArrayList<PreKey> = crypto!!.newPreKeys(0, 20)
        val lastKey: PreKey = crypto!!.newLastPreKey()
        return loginClient.registerClient(token, password, preKeys, lastKey)
    }

    val clientId: String?
        get() = try {
            storage!!.getState().client
        } catch (ex: IOException) {
            null
        }

    @Throws(CryptoException::class, IOException::class)
    fun getWireClient(conversationId: UUID?): WireClientImp {
        val state = storage!!.getState()
        val token = state.token
        val api = API(client, conversationId, token!!)
        return WireClientImp(api, crypto!!, state)
    }

    @Throws(IOException::class)
    fun updateState(userId: UUID, clientId: String, token: String, last: UUID?): NewBot? {
        val newBot = NewBot(id = userId, client = clientId, token = token, last = last, conversation = null, origin = null)
        storage!!.saveState(newBot)
        return storage!!.getState()
    }

    companion object {
        private const val SIZE = 100
    }

}
