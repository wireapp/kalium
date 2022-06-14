package com.wire.kalium.logic.data.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallActiveSpeakers(
    @SerialName("audio_levels") val activeSpeakers: List<CallActiveSpeaker>
)

@Serializable
data class CallActiveSpeaker(
    @SerialName("userid") val userId: String,
    @SerialName("clientid") val clientId: String,
    @SerialName("audio_level") val audioLevel: Int,
    @SerialName("audio_level_now") val audioLevelNow: Int
)
