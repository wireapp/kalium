package com.wire.kalium.api

interface AuthenticationManager {
    fun accessToken(): String
    fun refreshToken(): String
}
