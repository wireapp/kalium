package com.wire.kalium.api

import io.ktor.http.Cookie

interface AuthenticationManager {
    fun accessToken(): String
    fun refreshToken(): String
}
