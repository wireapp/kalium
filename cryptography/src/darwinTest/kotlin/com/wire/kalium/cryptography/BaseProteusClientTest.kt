package com.wire.kalium.cryptography

import kotlinx.coroutines.ExperimentalCoroutinesApi
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

@OptIn(ExperimentalCoroutinesApi::class)
actual open class BaseProteusClientTest actual constructor() {

    actual fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef {
        val rootDir = NSURL.fileURLWithPath(NSTemporaryDirectory() + "proteus/${userId.value}", isDirectory = true)
        return ProteusStoreRef(rootDir.path!!)
    }

    actual fun createProteusClient(
        proteusStore: ProteusStoreRef,
        databaseKey: ProteusDBSecret?
    ): ProteusClient {
        return ProteusClientImpl(proteusStore.value, defaultContext = standardScope, ioContext = standardScope)
    }

}
