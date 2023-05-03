package com.wire.kalium.network.networkContainer

import kotlin.native.concurrent.ThreadLocal

object KaliumUserAgentProvider {
    lateinit var userAgent: String
        private set
    fun setUserAgent(userAgent: String) {
        if (!this::userAgent.isInitialized) {
            this.userAgent = userAgent
        }
    }
}
