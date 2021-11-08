package com.wire.kalium.models.otr

import java.util.UUID

//<UserId, ClientCipher> //Base64 encoded cipher
class Recipients : HashMap<UUID?, ClientCipher?>() {
    operator fun get(userId: UUID?, clientId: String?): String? {
        val clients: HashMap<String?, String?>? = toClients(userId)
        return clients.get(clientId)
    }

    fun add(userId: UUID?, clientId: String?, cipher: String?) {
        val clients = toClients(userId)
        clients[clientId] = cipher
    }

    //<UserId, <ClientId, Cipher>>
    fun add(userId: UUID?, clients: ClientCipher?) {
        val clientIds = clients.keys
        for (clientId in clientIds) {
            val bytes = clients.get(clientId)
            add(userId, clientId, bytes)
        }
    }

    fun add(recipients: Recipients?) {
        val userIds = recipients.keys
        for (userId in userIds) {
            val hashMap = recipients.get(userId)
            add(userId, hashMap)
        }
    }

    private fun toClients(userId: UUID?): ClientCipher? {
        return computeIfAbsent(userId) { k: UUID? -> ClientCipher() }
    }
}
