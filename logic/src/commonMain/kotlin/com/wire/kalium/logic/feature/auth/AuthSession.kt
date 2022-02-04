package com.wire.kalium.logic.feature.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)
