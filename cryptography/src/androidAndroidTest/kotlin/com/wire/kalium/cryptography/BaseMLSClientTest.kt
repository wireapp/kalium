package com.wire.kalium.cryptography

import java.nio.file.Files

actual open class BaseMLSClientTest {

    actual fun createMLSClient(clientId: CryptoQualifiedClientId): MLSClient {
        val root = Files.createTempDirectory("mls").toFile()
        val keyStore = root.resolve("keystore-$clientId")
        return MLSClientImpl(keyStore.absolutePath, MlsDBSecret("test"), clientId)
    }

}
