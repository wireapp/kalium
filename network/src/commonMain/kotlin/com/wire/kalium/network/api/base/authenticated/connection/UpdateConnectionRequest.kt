package com.wire.kalium.network.api.base.authenticated.connection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateConnectionRequest(
    @SerialName("status") val status: ConnectionStateDTO
)
