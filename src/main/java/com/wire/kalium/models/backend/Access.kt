package com.wire.kalium.models.backend

import com.wire.kalium.models.system.Cookie
import com.wire.kalium.tools.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Access(
    @Serializable(with = UUIDSerializer::class) val user: UUID,
    val access_token: String,
    val expires_in: Int,
    val token_type: String,
) {
    var cookie: Cookie? = null
}
