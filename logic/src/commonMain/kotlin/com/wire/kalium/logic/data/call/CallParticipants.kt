package com.wire.kalium.logic.data.call

import kotlinx.serialization.Serializable

@Serializable
data class CallParticipants(
    val convid: String,
    val members: List<CallMember>
)

@Serializable
data class CallMember(
    val userid: String,
    val clientid: String,
    val aestab: Int,
    val vrecv: Int,
    val muted: Int
)
