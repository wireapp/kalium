package com.wire.kalium.api.message

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

    // TODO: fix serial error after a message is sent
    object MessageSent : SendMessageResponse()
}




// Map of userId to clientId
typealias MissingUsers = UserIdToClientMap
typealias RedundantUsers = UserIdToClientMap
typealias DeletedUsers = UserIdToClientMap

typealias UserIdToClientMap = HashMap<String, List<String>>
