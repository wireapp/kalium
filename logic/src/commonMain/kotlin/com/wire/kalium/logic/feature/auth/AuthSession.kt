package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.ServerConfig

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val serverConfig: ServerConfig
)
