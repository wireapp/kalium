package com.wire.kalium.cryptography

expect open class BaseProteusClientTest() {

    fun createProteusClient(userId: PlainUserId): ProteusClient

}
