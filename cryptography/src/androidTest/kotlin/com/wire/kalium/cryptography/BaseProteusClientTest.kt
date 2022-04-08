package com.wire.kalium.cryptography

import java.nio.file.Files

actual open class BaseProteusClientTest {

    actual fun createProteusClient(userId: UserId): ProteusClient {
        val root = Files.createTempDirectory("proteus").toFile()
        return ProteusClientImpl(root.resolve(userId.value).absolutePath)
    }
}

