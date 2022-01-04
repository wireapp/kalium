package com.wire.kalium.persistent.model

data class Session(
    val userId: String,
    val clientId: String,
    val domain: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)
