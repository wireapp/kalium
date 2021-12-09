package com.wire.kalium.logic

import com.wire.kalium.network.api.CredentialsProvider

data class AuthSession(
    val authToken: String,
    val refreshToken: String,
    val tokenType: String
) : CredentialsProvider { // TODO: Remove CredentialsProvider?
    override fun accessToken() = authToken

    override fun refreshToken() = refreshToken
}
