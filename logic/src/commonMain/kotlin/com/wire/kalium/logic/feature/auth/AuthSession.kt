package com.wire.kalium.logic.feature.auth

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)
