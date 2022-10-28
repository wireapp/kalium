package com.wire.kalium.cryptography

import kotlin.jvm.JvmInline

@JvmInline
value class ProteusStoreRef(val value: String)

expect open class BaseProteusClientTest() {

    fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef
    fun createProteusClient(proteusStore: ProteusStoreRef, databaseKey: ProteusDBSecret? = null): ProteusClient

}
