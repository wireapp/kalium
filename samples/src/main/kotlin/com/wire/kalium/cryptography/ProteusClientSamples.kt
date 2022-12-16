package com.wire.kalium.cryptography

suspend fun basicEncryption() {
    val proteusClient: ProteusClient = ProteusClientImpl("rootDir")
    proteusClient.encrypt(
        byteArrayOf(0x42, 0x69),
        CryptoSessionId(CryptoUserID("asdiuh", "aisudh"), CryptoClientId("aisudh"))
    )
}
