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

expect class ProteusSession {

    @Throws(ProteusException::class)
    fun encrypt(data: ByteArray): ByteArray
    @Throws(ProteusException::class)
    fun decrypt(data: ByteArray): ByteArray
}

expect class ProteusClient(rootDir: String, userId: String) {

    @Throws(ProteusException::class)
    fun open()
    @Throws(ProteusException::class)
    fun close()

    @Throws(ProteusException::class)
    fun getIdentity(): ByteArray
    @Throws(ProteusException::class)
    fun getLocalFingerprint(): ByteArray

    @Throws(ProteusException::class)
    fun newPreKeys(from: Int, count: Int): ArrayList<PreKey>
    @Throws(ProteusException::class)
    fun newLastPreKey(): PreKey

    fun getSession(sessionId: CryptoSessionId): ProteusSession?
    @Throws(ProteusException::class)
    fun createSession(preKey: PreKey, sessionId: CryptoSessionId): ProteusSession
    @Throws(ProteusException::class)
    fun createSessionAndDecrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray
}

fun ProteusClient.createSessions(preKeys: Map<String, Map<String, PreKey>>) {
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
