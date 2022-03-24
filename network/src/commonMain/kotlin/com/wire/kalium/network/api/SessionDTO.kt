package com.wire.kalium.network.api

data class SessionDTO(
    val userIdValue: QualifiedID,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)
