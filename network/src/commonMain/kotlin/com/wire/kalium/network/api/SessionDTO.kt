package com.wire.kalium.network.api



data class SessionDTO(
    val userIdValue: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)

