package com.wire.kalium.cryptography

actual open class BaseProteusClientTest actual constructor() {

    actual fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef {
        // TODO currently expects an in memory proteus client
        return ProteusStoreRef("ref")
    }

    actual fun createProteusClient(
        proteusStore: ProteusStoreRef,
        databaseKey: ProteusDBSecret?
    ): ProteusClient {
        return ProteusClientImpl(proteusStore.value)
    }

}
