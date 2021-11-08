package com.wire.kalium.models.otr

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.ArrayList

//<UserId, [ClientId]>
class Missing : ConcurrentHashMap<UUID?, MutableCollection<String?>?>() {
    fun toClients(userId: UUID?): MutableCollection<String?>? {
        return get(userId)
    }

    fun toUserIds(): MutableCollection<UUID?>? {
        return keys
    }

    fun add(userId: UUID?, clientId: String?) {
        val clients = computeIfAbsent(userId) { k: UUID? -> ArrayList() }
        clients.add(clientId)
    }

    fun add(userId: UUID?, clients: MutableCollection<String?>?) {
        val old = computeIfAbsent(userId) { k: UUID? -> ArrayList() }
        old.addAll(clients)
    }
}
