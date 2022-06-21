package com.wire.kalium.logic.data.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("EnforceSerializableFields")
@Serializable
data class CallParticipants(
    @SerialName("convid")
    val conversationId: String,
    val members: List<CallMember>
)

@Suppress("EnforceSerializableFields")
@Serializable
data class CallMember(
    @SerialName("userid")
    val userId: String,
    @SerialName("clientid")
    val clientId: String,
    val aestab: Int,
    val vrecv: Int,
    @SerialName("muted")
    val isMuted: Int
)
