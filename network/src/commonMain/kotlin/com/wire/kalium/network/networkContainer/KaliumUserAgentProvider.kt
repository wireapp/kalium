package com.wire.kalium.network.networkContainer

object KaliumUserAgentProvider {
    lateinit var userAgent: String
        private set
    fun setUserAgent(userAgent: String) {
        if (!this::userAgent.isInitialized) {
            this.userAgent = userAgent
        }
    }
}
