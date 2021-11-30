package com.wire.kalium.cli

import com.wire.kalium.api.AuthenticationManager

class AuthenticationManagerImpl(
        private val accessToken: String,
        private val tokenType: String,
        val refreshToken: String
) : AuthenticationManager {
    override fun accessToken(): String {
        return accessToken
    }

    override fun refreshToken(): String = refreshToken
}
