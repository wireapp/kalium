package com.wire.kalium.network.api

import com.wire.kalium.network.api.user.login.LoginWithEmailResponse

data class SessionCredentials(
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)

fun LoginWithEmailResponse.toSessionCredentials(refreshToken: String): SessionCredentials = SessionCredentials(
    tokenType = tokenType,
    accessToken = accessToken,
    refreshToken = refreshToken
)
