package com.wire.kalium.cli

import com.wire.kalium.network.api.AuthenticationManager


class AuthenticationManagerImpl(
    private val accessToken: String,
    private val refreshToken: String
) : AuthenticationManager {
    override fun accessToken(): String = accessToken

    override fun refreshToken(): String = refreshToken
}

