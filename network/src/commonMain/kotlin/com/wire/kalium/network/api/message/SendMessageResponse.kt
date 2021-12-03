package com.wire.kalium.network.api.message

import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class SendMessageResponse {
    @Serializable
    data class MissingDevicesResponse(
        @SerialName("time") val time: String,
        @SerialName("missing") val missing: MissingUsers,
        @SerialName("redundant") val redundant: RedundantUsers,
        @SerialName("deleted") val deleted: DeletedUsers
    ) : SendMessageResponse()

    @Serializable
    data class MessageSent(
        @SerialName("time") val time: String,
        @SerialName("missing") val missing: MissingUsers,
        @SerialName("redundant") val redundant: RedundantUsers,
        @SerialName("deleted") val deleted: DeletedUsers
    ) : SendMessageResponse()
}

// Map of userId to clientId
typealias MissingUsers = UserIdToClientMap
typealias RedundantUsers = UserIdToClientMap
typealias DeletedUsers = UserIdToClientMap

typealias UserIdToClientMap = HashMap<String, List<String>>
typealias QualifiedUserIdToClientMap = HashMap<UserId, List<String>>

@Serializable
enum class MessagePriority {
    @SerialName("low")
    LOW,

    @SerialName("high")
    HIGH
}
