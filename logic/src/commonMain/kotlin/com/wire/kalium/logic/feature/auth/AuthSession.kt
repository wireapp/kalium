package com.wire.kalium.logic.feature.auth

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)
