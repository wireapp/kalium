package com.wire.kalium.backend.models

import java.util.UUID

data class SystemMessage(
    val id: UUID,
    val type: String,
    val time: String,
    val from: UUID,
    val conversation: Conversation,
    val convId: UUID,
    val userIds: List<UUID>
)
