package com.wire.kalium.logic.data.call

import kotlinx.serialization.Serializable

@Serializable
data class AvsParticipants(
    val convid: String,
    val members: List<AvsMember>
)

@Serializable
data class AvsMember(
    val userid: String,
    val clientid: String,
    val aestab: Int,
    val vrecv: Int,
    val muted: Int
)
