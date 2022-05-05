package com.wire.kalium.logic.data.call

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CallClient(
    val userId: String,
    val clientId: String
)

@Serializable
data class CallClientList(
    val clients: List<CallClient>
) {
    fun toJsonString(): String = Json { isLenient = true }.encodeToString(serializer(), this)
}
