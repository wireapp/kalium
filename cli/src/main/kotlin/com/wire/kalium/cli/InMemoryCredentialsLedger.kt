package com.wire.kalium.cli

import com.wire.kalium.network.api.CredentialsProvider

class InMemoryCredentialsLedger : CredentialsProvider {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    fun onAuthenticate(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override fun accessToken(): String = accessToken!!

    override fun refreshToken(): String = refreshToken!!
}
