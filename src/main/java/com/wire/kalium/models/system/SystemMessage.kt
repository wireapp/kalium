package com.wire.kalium.models.system

import com.wire.kalium.models.backend.Conversation
import com.wire.kalium.tools.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class SystemMessage(
        @Serializable(with = UUIDSerializer::class) val id: UUID,
        val type: String,
        val time: String,
        @Serializable(with = UUIDSerializer::class) val from: UUID,
        val conversation: Conversation,
        @Serializable(with = UUIDSerializer::class) val convId: UUID,
        val userIds: List<@Serializable(with = UUIDSerializer::class) UUID>? = null
)
