package com.wire.kalium.network.api

interface AuthenticationManager {
    fun accessToken(): String
    fun refreshToken(): String
}
