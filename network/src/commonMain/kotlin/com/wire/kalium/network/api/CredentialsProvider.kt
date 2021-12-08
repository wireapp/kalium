package com.wire.kalium.network.api

interface CredentialsProvider {
    fun accessToken(): String
    fun refreshToken(): String
}
