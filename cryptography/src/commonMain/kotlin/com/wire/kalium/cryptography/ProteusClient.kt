package com.wire.kalium.cryptography

expect class ProteusClient {

    fun createSession(preKey: String, sessionId: String)
}
