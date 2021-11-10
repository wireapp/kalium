package com.wire.helium

import com.wire.bots.cryptobox.CryptoException
import com.wire.xenon.MessageHandlerBase
import com.wire.xenon.MessageResourceBase
import com.wire.xenon.WireClient
import com.wire.xenon.backend.models.NewBot
import com.wire.xenon.backend.models.Payload
import com.wire.xenon.crypto.Crypto
import com.wire.xenon.factories.CryptoFactory
import com.wire.xenon.factories.StorageFactory
import com.wire.xenon.state.State
import com.wire.xenon.tools.Logger
import java.io.IOException
import java.util.*
import javax.ws.rs.client.Client

class UserMessageResource(handler: MessageHandlerBase?) : MessageResourceBase(handler) {
    private var userId: UUID? = null
    private var storageFactory: StorageFactory? = null
    private var cryptoFactory: CryptoFactory? = null
    private var client: Client? = null

    @get:Throws(CryptoException::class)
    private var crypto: Crypto? = null
        private get() {
            if (field == null) field = cryptoFactory.create(userId)
            return field
        }
    private var state: State? = null

    @Throws(Exception::class)
    fun onNewMessage(eventId: UUID?, convId: UUID?, payload: Payload) {
        if (convId == null) {
            Logger.warning("onNewMessage: %s convId is null", payload.type)
            return
        }
        try {
            val client: WireClient = getWireClient(convId)
            handleMessage(eventId, payload, client)
        } catch (e: CryptoException) {
            Logger.exception("onNewMessage: msg: %s, conv: %s, %s", e, eventId, convId, e.message)
        }
    }

    @Throws(CryptoException::class, IOException::class)
    fun onUpdate(id: UUID?, payload: Payload) {
        handleUpdate(id, payload, getWireClient(null))
    }

    @Throws(CryptoException::class, IOException::class)
    fun getWireClient(convId: UUID?): WireClientImp {
        val crypto: Crypto? = crypto
        val newBot: NewBot = getState()
        val api: com.wire.helium.API = com.wire.helium.API(client, convId, newBot.token)
        return WireClientImp(api, crypto, newBot, convId!!)
    }

    @Throws(IOException::class)
    private fun getState(): NewBot {
        if (state == null) state = storageFactory.create(userId)
        return state.getState()
    }

    fun addUserId(userId: UUID?): UserMessageResource {
        this.userId = userId
        return this
    }

    fun addStorageFactory(storageFactory: StorageFactory?): UserMessageResource {
        this.storageFactory = storageFactory
        return this
    }

    fun addCryptoFactory(cryptoFactory: CryptoFactory?): UserMessageResource {
        this.cryptoFactory = cryptoFactory
        return this
    }

    fun addClient(client: Client?): UserMessageResource {
        this.client = client
        return this
    }

    protected fun handleUpdate(id: UUID?, payload: Payload, userClient: WireClientImp?) {
        when (payload.type) {
            "team.member-join" -> {
                Logger.debug("%s: team: %s, user: %s", payload.type, payload.team, payload.data.user)
                handler.onNewTeamMember(userClient, payload.data.user)
            }
            "user.update" -> {
                Logger.debug("%s: id: %s", payload.type, payload.user.id)
                handler.onUserUpdate(id, payload.user.id)
            }
            else -> Logger.debug("Unknown event: %s", payload.type)
        }
    }
}