package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.exceptions.ProteusException

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

data class PreKey(
    val id: Int,
    val encodedData: String
)

expect class ProteusClient(rootDir: String, userId: String) {

    @Throws(ProteusException::class)
    suspend fun open()
    @Throws(ProteusException::class)
    fun close()

    @Throws(ProteusException::class)
    fun getIdentity(): ByteArray
    @Throws(ProteusException::class)
    fun getLocalFingerprint(): ByteArray

    @Throws(ProteusException::class)
    suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKey>
    @Throws(ProteusException::class)
    fun newLastPreKey(): PreKey

    @Throws(ProteusException::class)
    suspend fun createSession(preKey: PreKey, sessionId: CryptoSessionId)

    @Throws(ProteusException::class)
    suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray

    @Throws(ProteusException::class)
    suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray?

    @Throws(ProteusException::class)
    suspend fun encryptWithPreKey(message: ByteArray, preKey: PreKey, sessionId: CryptoSessionId): ByteArray
}

suspend fun ProteusClient.createSessions(preKeys: Map<String, Map<String, PreKey>>) {
    for (userId in preKeys.keys) {
        val clients = preKeys.getValue(userId)
        for (clientId in clients.keys) {
            val pk = clients[clientId]
            if (pk != null) {
                val id = CryptoSessionId(UserId(userId), CryptoClientId(clientId))
                createSession(pk, id)
            }
        }
    }
}
