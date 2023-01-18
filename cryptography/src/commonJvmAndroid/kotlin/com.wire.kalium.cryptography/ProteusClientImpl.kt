package com.wire.kalium.cryptography

// TODO remove after debugging is completed
var instanceCounter = 0

@Suppress("TooManyFunctions")
/**
 * @sample samples.cryptography.jvmInitialization
 */
actual class ProteusClientImpl actual constructor(rootDir: String, databaseKey: ProteusDBSecret?) : ProteusClient {

    private var client: ProteusClient = (databaseKey?.let {
        ProteusClientCoreCryptoImpl(rootDir, it)
    } ?: ProteusClientCryptoBoxImpl(rootDir)).also { kaliumLogger.d("Constructing ProteusClient #${instanceCounter++} at $rootDir") }

    override fun clearLocalFiles(): Boolean {
        return client.clearLocalFiles()
    }

    override fun needsMigration(): Boolean {
        return client.needsMigration()
    }

    override suspend fun openOrCreate() {
        client.openOrCreate()
    }

    override suspend fun openOrError() {
        client.openOrError()
    }

    override fun getIdentity(): ByteArray {
        return client.getIdentity()
    }

    override fun getLocalFingerprint(): ByteArray {
        return client.getLocalFingerprint()
    }

    override suspend fun newPreKeys(from: Int, count: Int): List<PreKeyCrypto> {
        return client.newPreKeys(from, count)
    }

    override fun newLastPreKey(): PreKeyCrypto {
        return client.newLastPreKey()
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean {
        return client.doesSessionExist(sessionId)
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        return client.createSession(preKeyCrypto, sessionId)
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return client.decrypt(message, sessionId)
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return client.encrypt(message, sessionId)
    }

    override suspend fun encryptWithPreKey(message: ByteArray, preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId): ByteArray {
        return client.encryptWithPreKey(message, preKeyCrypto, sessionId)
    }

    override fun deleteSession(sessionId: CryptoSessionId) {
        client.deleteSession(sessionId)
    }
}
