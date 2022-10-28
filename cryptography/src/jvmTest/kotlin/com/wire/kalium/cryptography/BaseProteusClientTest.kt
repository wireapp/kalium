package com.wire.kalium.cryptography

import java.nio.file.Files

actual open class BaseProteusClientTest {

    actual fun createProteusClient(userId: CryptoUserID): ProteusClient {
        val root = Files.createTempDirectory("proteus").toFile()
        val keyStore = root.resolve("keystore-${userId.value}")
        return ProteusClientImpl(keyStore.absolutePath)
    }

}
