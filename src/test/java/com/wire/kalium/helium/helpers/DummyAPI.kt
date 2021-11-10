package com.wire.helium.helpers

import com.wire.bots.cryptobox.PreKey
import com.wire.kalium.helium.API
import com.wire.xenon.models.otr.*
import org.glassfish.jersey.client.JerseyClientBuilder
import java.util.*

class DummyAPI : API(JerseyClientBuilder.createClient(), null, null) {
    private val devices: Devices = Devices()
    private val lastPreKeys: HashMap<String, PreKey> = HashMap<String, PreKey>() // <userId-clientId, PreKey>
    private var msg: OtrMessage? = null
    override fun sendMessage(msg: OtrMessage, vararg ignoreMissing: Any?): Devices {
        this.msg = msg
        val missing = Devices()
        for (userId in devices.missing.toUserIds()) {
            for (client in devices.missing.toClients(userId)) {
                if (msg.get(userId, client) == null) missing.missing.add(userId, client)
            }
        }
        return missing
    }

    override fun getPreKeys(missing: Missing): PreKeys {
        val ret = PreKeys()
        for (userId in missing.toUserIds()) {
            val devs: HashMap<String, PreKey?> = HashMap<String, PreKey?>()
            for (client in missing.toClients(userId)) {
                val key = key(userId, client)
                val preKey: PreKey? = lastPreKeys[key]
                devs[client] = preKey
            }
            ret.put(userId, devs)
        }
        return ret
    }

    private fun convert(lastKey: PreKey): PreKey {
        val preKey = PreKey()
        preKey.id = lastKey.id
        preKey.key = Base64.getEncoder().encodeToString(lastKey.data)
        return preKey
    }

    fun addDevice(userId: UUID, client: String, lastKey: PreKey) {
        devices.missing.add(userId, client)
        addLastKey(userId, client, lastKey)
    }

    private fun addLastKey(userId: UUID, clientId: String, lastKey: PreKey) {
        val key = key(userId, clientId)
        val preKey: PreKey = convert(lastKey)
        lastPreKeys[key] = preKey
    }

    private fun key(userId: UUID, clientId: String): String {
        return String.format("%s-%s", userId, clientId)
    }

    fun getMsg(): OtrMessage? {
        return msg
    }
}
