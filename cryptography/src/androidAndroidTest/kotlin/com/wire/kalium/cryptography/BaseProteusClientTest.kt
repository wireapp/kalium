package com.wire.kalium.cryptography

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
actual open class BaseProteusClientTest {

    private val standardScope = StandardTestDispatcher()

    actual fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef {
        val root = Files.createTempDirectory("proteus").toFile()
        val keyStore = root.resolve("keystore-${userId.value}")
        return ProteusStoreRef(keyStore.absolutePath)
    }

    actual fun createProteusClient(proteusStore: ProteusStoreRef, databaseKey: ProteusDBSecret?): ProteusClient {
        return ProteusClientImpl(proteusStore.value, databaseKey, defaultContext = standardScope, ioContext = standardScope)
    }
}
