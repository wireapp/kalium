package com.wire.kalium.cryptography

actual open class BaseProteusClientTest actual constructor() {

    actual fun createProteusClient(userId: CryptoUserID): ProteusClient {
        // TODO currently expects an in memory proteus client
        return ProteusClientImpl("foo/bar")
    }

}
