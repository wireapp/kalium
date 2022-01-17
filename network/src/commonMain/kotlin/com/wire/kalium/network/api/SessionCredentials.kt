package com.wire.kalium.network.api

import com.wire.kalium.network.api.user.login.LoginResponse

data class SessionCredentials(
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)

fun LoginResponse.toSessionCredentials(refreshToken: String): SessionCredentials = SessionCredentials(
    tokenType = tokenType,
    accessToken = accessToken,
    refreshToken = refreshToken
)
