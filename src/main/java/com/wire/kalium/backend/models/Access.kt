package com.wire.kalium.backend.models

import com.wire.kalium.tools.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Access(
        var cookie: Cookie,
        @Serializable(with = UUIDSerializer::class) val userId: UUID,
        val access_token: String,
        val expires_in: Int,
        val token_type: String,
)
