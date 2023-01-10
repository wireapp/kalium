package com.wire.kalium.cryptography

@Suppress("TooManyFunctions")
actual class ProteusClientImpl actual constructor(private val rootDir: String, databaseKey: ProteusDBSecret?) : ProteusClient {
    override fun clearLocalFiles(): Boolean {
        TODO("Not yet implemented")
    }

    override fun needsMigration(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun openOrCreate() {
        TODO("Not yet implemented")
    }

    override suspend fun openOrError() {
        TODO("Not yet implemented")
    }

    override fun getIdentity(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun getLocalFingerprint(): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun newPreKeys(from: Int, count: Int): List<PreKeyCrypto> {
        TODO("Not yet implemented")
    }

    override fun newLastPreKey(): PreKeyCrypto {
        TODO("Not yet implemented")
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        TODO("Not yet implemented")
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray> {
        TODO("Not yet implemented")
    }

    override suspend fun encryptWithPreKey(message: ByteArray, preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deleteSession(sessionId: CryptoSessionId) {
        TODO("Not yet implemented")
    }

}
