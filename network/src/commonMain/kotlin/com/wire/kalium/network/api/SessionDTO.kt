package com.wire.kalium.network.api

data class SessionDTO(
    val userId: QualifiedID,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)
