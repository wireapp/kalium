package com.wire.kalium.models.outbound.otr

import kotlinx.serialization.Serializable
import java.util.*

//<UserId, ClientCipher> //Base64 encoded cipher
@Serializable
class Recipients : HashMap<UUID, ClientCipher>() {
    operator fun get(userId: UUID, clientId: String): String {
        val clients = toClients(userId)
        return clients.getValue(clientId)
    }

    fun add(userId: UUID, clientId: String, cipher: String) {
        val clients = toClients(userId)
        clients[clientId] = cipher
    }

    //<UserId, <ClientId, Cipher>>
    fun add(userId: UUID, clients: ClientCipher) {
        val clientIds = clients.keys
        for (clientId in clientIds) {
            val bytes = clients.getValue(clientId)
            add(userId, clientId, bytes)
        }
    }

    fun add(recipients: Recipients) {
        val userIds = recipients.keys
        for (userId in userIds) {
            val hashMap = recipients.getValue(userId)
            add(userId, hashMap)
        }
    }

    private fun toClients(userId: UUID): ClientCipher {
        return computeIfAbsent(userId) { k: UUID -> ClientCipher() }
    }
}
