package com.wire.kalium.cryptography

expect open class BaseProteusClientTest() {

    fun createProteusClient(userId: CryptoUserID): ProteusClient

}
