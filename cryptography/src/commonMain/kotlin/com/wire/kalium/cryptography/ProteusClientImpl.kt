package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.exceptions.ProteusException
import kotlin.coroutines.cancellation.CancellationException

data class CryptoClientId(val value: String) {
    override fun toString() = value
}

data class UserId(val value: String) {
    override fun toString() = value
}

data class CryptoSessionId(val userId: UserId, val cryptoClientId: CryptoClientId) {
    //TODO Take domain into consideration here too
    val value: String = "${userId}_${cryptoClientId}"
}

data class PreKeyCrypto(
    val id: Int,
    val encodedData: String
)

interface ProteusClient {

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun open()

    @Throws(ProteusException::class)
    fun close()

    @Throws(ProteusException::class)
    fun getIdentity(): ByteArray

    @Throws(ProteusException::class)
    fun getLocalFingerprint(): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun newPreKeys(from: Int, count: Int): List<PreKeyCrypto>

    @Throws(ProteusException::class)
    fun newLastPreKey(): PreKeyCrypto

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId)

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun encryptWithPreKey(message: ByteArray, preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId): ByteArray
}

expect class ProteusClientImpl(rootDir: String, userId: String): ProteusClient

suspend fun ProteusClient.createSessions(preKeysCrypto: Map<String, Map<String, PreKeyCrypto>>) {
    for (userId in preKeysCrypto.keys) {
        val clients = preKeysCrypto.getValue(userId)
        for (clientId in clients.keys) {
            val pk = clients[clientId]
            if (pk != null) {
                val id = CryptoSessionId(UserId(userId), CryptoClientId(clientId))
                createSession(pk, id)
            }
        }
    }
}
