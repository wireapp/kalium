package com.wire.kalium.logic.data.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CallClient(
    @SerialName("userid") val userId: String,
    @SerialName("clientid") val clientId: String
)

@Serializable
data class CallClientList(
    val clients: List<CallClient>
) {
    fun toJsonString(): String = Json { isLenient = true }.encodeToString(serializer(), this)
}
