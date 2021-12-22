package com.wire.kalium.network.api.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SendMessageResponse {
    abstract val time: String
    abstract val missing: UserIdToClientMap
    abstract val redundant: UserIdToClientMap
    abstract val deleted: UserIdToClientMap

    @Serializable
    data class MissingDevicesResponse(
        override val time: String,
        override val missing: UserIdToClientMap,
        override val redundant: UserIdToClientMap,
        override val deleted: UserIdToClientMap
    ) : SendMessageResponse()

    @Serializable
    data class MessageSent(
        override val time: String,
        override val missing: UserIdToClientMap,
        override val redundant: UserIdToClientMap,
        override val deleted: UserIdToClientMap
    ) : SendMessageResponse()
}

typealias UserIdToClientMap = Map<String, List<String>>

@Serializable
enum class MessagePriority {
    @SerialName("low")
    LOW,

    @SerialName("high")
    HIGH;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
