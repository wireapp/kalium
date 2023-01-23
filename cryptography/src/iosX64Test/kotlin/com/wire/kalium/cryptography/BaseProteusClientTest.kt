package com.wire.kalium.cryptography

import kotlinx.coroutines.test.StandardTestDispatcher
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

actual open class BaseProteusClientTest actual constructor() {

    private val standardScope = StandardTestDispatcher()

    actual fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef {
        val rootDir = NSURL.fileURLWithPath(NSTemporaryDirectory() + "/proteus/${userId.value}", isDirectory = true)
        return ProteusStoreRef(rootDir.absoluteString!!)
    }

    actual fun createProteusClient(
        proteusStore: ProteusStoreRef,
        databaseKey: ProteusDBSecret?
    ): ProteusClient {
        return ProteusClientImpl(proteusStore.value, ioContext = standardScope, defaultContext = standardScope)
    }

}
