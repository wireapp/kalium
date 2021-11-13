package com.wire.kalium.models.outbound.otr

import kotlinx.serialization.Serializable
import java.util.*

//<UserId, [ClientId]>
@Serializable

class Missing : HashMap<UUID, MutableCollection<String>>() {
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
