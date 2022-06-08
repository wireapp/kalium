package com.wire.kalium.logic.data.call

data class ActiveSpeaker(
    val userId: String,
    val clientId: String,
    val audioLevel: Int,
    val audioLevelNow: Int
)
