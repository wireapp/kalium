package com.wire.kalium.helium

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.backend.models.NewBot
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.helium.models.Access
import com.wire.kalium.models.otr.PreKey
import com.wire.kalium.tools.Logger
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.ws.rs.client.Client
import javax.ws.rs.core.Cookie

class Application(protected val email: String, protected val password: String) {

    protected val renewal: ScheduledExecutorService

    protected var storageFactory: StorageFactory? = null
    protected var cryptoFactory: CryptoFactory? = null
    protected var client: Client? = null
    protected var userId: UUID? = null
        private set
    private var loginClient: LoginClient? = null
    private var cookie: Cookie? = null

    fun addClient(client: Client): Application {
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
        val access: Access = loginClient!!.login(email, password, true)
        userId = access.userId
        cookie = convert(access.getCookie())
        var clientId = clientId
        if (clientId == null) {
            clientId = newDevice(userId!!, password, access.getAccessToken())
            Logger.info("Created new device. clientId: %s", clientId)
        }
        var state: NewBot = updateState(userId!!, clientId, access.getAccessToken(), null)
        Logger.info("Logged in as: %s, userId: %s, clientId: %s", email, state.id, state.client)
        val deviceId: String = state.client
        renewal.scheduleAtFixedRate({
            try {
                val newAccess: Access = loginClient!!.renewAccessToken(cookie)
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
    }

    private fun convert(cookie: Cookie): Cookie {
        return Cookie(cookie.name, cookie.value)
    }

    @Throws(CryptoException::class, HttpException::class)
    fun newDevice(userId: UUID, password: String, token: String): String {
        val crypto: Crypto = cryptoFactory.create(userId)
        val loginClient = LoginClient(client!!)
        val preKeys: ArrayList<PreKey> = crypto.newPreKeys(0, 20)
        val lastKey: PreKey = crypto.newLastPreKey()
        return loginClient.registerClient(token, password, preKeys, lastKey)
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
            newBot.setToken(token)
        } catch (ex: IOException) {
            newBot = NewBot(id = userId, client = clientId, token = token)
        }

        if (last != null)
            newBot.locale = last.toString()  //todo: hahaha... rename locale to Last

        state.saveState(newBot)
        return state.getState()
    }

    companion object {
        private const val SIZE = 100
    }

    init {
        renewal = Executors.newScheduledThreadPool(1)
    }
}
