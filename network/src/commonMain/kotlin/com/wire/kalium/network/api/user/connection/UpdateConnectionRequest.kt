package com.wire.kalium.network.api.user.connection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateConnectionRequest(
    @SerialName("status") val status: ConnectionStateDTO
)
