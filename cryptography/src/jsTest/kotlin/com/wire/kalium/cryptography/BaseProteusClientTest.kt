package com.wire.kalium.cryptography

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

@OptIn(ExperimentalCoroutinesApi::class)
actual open class BaseProteusClientTest actual constructor() {

    private val standardScope = TestCoroutineScheduler()

    actual fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef {
        // TODO currently expects an in memory proteus client
        return ProteusStoreRef("ref")
    }

    actual fun createProteusClient(
        proteusStore: ProteusStoreRef,
        databaseKey: ProteusDBSecret?
    ): ProteusClient {
        return ProteusClientImpl(proteusStore.value, defaultContext = standardScope, ioContext = standardScope
    }

}
