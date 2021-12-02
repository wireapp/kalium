package com.wire.kalium.models.backend

import com.wire.kalium.models.system.Cookie
import com.wire.kalium.tools.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Access(
    @Serializable(with = UUIDSerializer::class)
    @SerialName("user") val user: UUID,
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expires_in: Int,
    @SerialName("token_type") val token_type: String,
) {
    var cookie: Cookie? = null
}
