package com.wire.kalium.network.api.message

import kotlinx.serialization.Serializable

@Serializable
sealed class QualifiedSendMessageResponse {
    abstract val time: String
    abstract val missing: QualifiedUserIdToClientMap
    abstract val redundant: QualifiedUserIdToClientMap
    abstract val deleted: QualifiedUserIdToClientMap

    @Serializable
    data class MissingDevicesResponse(
        override val time: String,
        override val missing: QualifiedUserIdToClientMap,
        override val redundant: QualifiedUserIdToClientMap,
        override val deleted: QualifiedUserIdToClientMap
    ) : QualifiedSendMessageResponse()

    @Serializable
    data class MessageSent(
        override val time: String,
        override val missing: QualifiedUserIdToClientMap,
        override val redundant: QualifiedUserIdToClientMap,
        override val deleted: QualifiedUserIdToClientMap
    ) : QualifiedSendMessageResponse()
}

typealias QualifiedUserIdToClientMap = Map<String, Map<String, List<String>>>
