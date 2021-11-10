package com.wire.kalium.models.otr

import java.util.*
import java.util.concurrent.ConcurrentHashMap

//<UserId, [ClientId]>
class Missing : ConcurrentHashMap<UUID, MutableCollection<String>>() {
    fun ofUser(userId: UUID): MutableCollection<String>? {
        return get(userId)
    }

    fun toUserIds(): MutableCollection<UUID> {
        return keys
    }

    fun add(userId: UUID, clientId: String) {
        val clients = computeIfAbsent(userId) { k: UUID -> ArrayList() }
        clients.add(clientId)
    }

    fun add(userId: UUID, clients: MutableCollection<String>) {
        val old = computeIfAbsent(userId) { k: UUID -> ArrayList() }
        old.addAll(clients)
    }
}
