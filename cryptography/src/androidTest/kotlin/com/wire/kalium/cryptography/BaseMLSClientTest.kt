package com.wire.kalium.cryptography

actual open class BaseMLSClientTest {

    actual fun createMLSClient(clientId: CryptoQualifiedClientId): MLSClient {
        val root = Files.createTempDirectory("mls").toFile()
        val keyStore = root.resolve("keystore-$clientId")
        return MLSClientImpl(keyStore.absolutePath, "test", clientId)
    }

}
