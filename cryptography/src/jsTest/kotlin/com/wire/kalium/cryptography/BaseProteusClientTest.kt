package com.wire.kalium.cryptography

actual open class BaseProteusClientTest actual constructor() {

    actual fun createProteusClient(userId: UserId): ProteusClient {
        // TODO currently expects an in memory proteus client
        return ProteusClient("foo/bar", userId.value)
    }

}
