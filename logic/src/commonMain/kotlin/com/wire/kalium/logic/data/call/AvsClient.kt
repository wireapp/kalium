package com.wire.kalium.logic.data.call

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AvsClient(
    val userId: String,
    val clientId: String
)

@Serializable
data class AvsClientList(
    val clients: List<AvsClient>
) {
    fun toJsonString(): String = Json { isLenient = true }.encodeToString(serializer(), this)
}
