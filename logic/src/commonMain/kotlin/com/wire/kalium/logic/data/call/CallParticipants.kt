package com.wire.kalium.logic.data.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallParticipants(
    @SerialName("convid") val convId: String,
    @SerialName("members") val members: List<CallMember>
)

@Serializable
data class CallMember(
    @SerialName("userid") val userId: String,
    @SerialName("clientid") val clientId: String,
    @SerialName("aestab") val aestab: Int,
    @SerialName("vrecv") val vrecv: Int,
    @SerialName("muted") val muted: Int
)
