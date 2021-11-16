package com.wire.kalium

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.models.backend.Access
import com.wire.kalium.models.outbound.otr.PreKey
import com.wire.kalium.tools.Logger
import java.io.IOException
import java.util.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.Cookie

class Application(private val email: String, private val password: String) {

    //protected val renewal: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    protected var crypto: Crypto? = null
    protected lateinit var client: Client
        private set
    protected var access: Access? = null
    protected var clientId: String? = null
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

    @Throws(Exception::class)
    fun stop() {
        Logger.info("Logging out...")
        loginClient.logout(cookie, access!!.access_token)
    }

    @Throws(Exception::class)
    fun start() {
        loginClient = LoginClient(client)
        access = loginClient.login(email, password)

        // FIXME: converting cookie into cookie
        //cookie = convert(access.cookie.toJavaxCookie())

        clientId = createNewDevice(password, access!!.access_token)

        Logger.info("Logged in as: %s, userId: %s, clientId: %s", email, access!!.user, clientId)

//        renewal.scheduleAtFixedRate({
//            try {
//                val newAccess: Access = loginClient.renewAccessToken(cookie)
//                updateState(userId!!, clientId, newAccess.access_token, null)
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
    private fun createNewDevice(password: String, token: String): String? {
        val loginClient = LoginClient(client)
        val preKeys: ArrayList<PreKey> = crypto!!.newPreKeys(0, 20)
        val lastKey: PreKey = crypto!!.newLastPreKey()
        return loginClient.registerClient(token, password, preKeys, lastKey)
    }

    @Throws(CryptoException::class, IOException::class)
    fun getWireClient(conversationId: UUID?): WireClient {
        val api = API(client, conversationId, access!!.access_token)
        return WireClient(api, crypto!!, access!!, clientId!!)
    }
}
