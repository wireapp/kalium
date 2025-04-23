/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.exceptions.ProteusException
import io.mockative.Mockable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

data class CryptoSessionId(val userId: CryptoUserID, val cryptoClientId: CryptoClientId) {
    val value: String = "${userId}_$cryptoClientId"

    companion object {
        private const val SESSION_ID_COMPONENT_COUNT = 2

        fun fromEncodedString(value: String): CryptoSessionId? {
            val components = value.split("_")
            if (components.size != SESSION_ID_COMPONENT_COUNT) return null

            val userId = CryptoUserID.fromEncodedString(components[0])
            val clientId = CryptoClientId(components[1])

            return if (userId != null) {
                CryptoSessionId(userId, clientId)
            } else {
                null
            }
        }
    }
}

data class PreKeyCrypto(
    val id: Int,
    val encodedData: String
)

@Suppress("TooManyFunctions")
/**
 * @sample samples.cryptography.ProteusClient.basicEncryption
 */
@Mockable
interface ProteusClient {

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun close()

    @Throws(ProteusException::class, CancellationException::class)
    fun getIdentity(): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun getLocalFingerprint(): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun remoteFingerPrint(sessionId: CryptoSessionId): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): ByteArray

    suspend fun newPreKeys(from: Int, count: Int): List<PreKeyCrypto>

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun newLastResortPreKey(): PreKeyCrypto

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId)

    /**
     * Decrypts a message.
     * In case of success, calls [handleDecryptedMessage] with the decrypted bytes.
     * @throws ProteusException in case of failure
     * @throws CancellationException
     */
    @Throws(ProteusException::class, CancellationException::class)
    suspend fun <T : Any> decrypt(
        message: ByteArray,
        sessionId: CryptoSessionId,
        handleDecryptedMessage: suspend (decryptedMessage: ByteArray) -> T
    ): T

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray>

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun encryptWithPreKey(message: ByteArray, preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun deleteSession(sessionId: CryptoSessionId)
}

suspend fun ProteusClient.createSessions(preKeysCrypto: Map<String, Map<String, Map<String, PreKeyCrypto>>>) {
    preKeysCrypto.forEach { domainMap ->
        val domain = domainMap.key
        domainMap.value.forEach { userToClientsMap ->
            val userId = userToClientsMap.key
            userToClientsMap.value.forEach { clientsToPreKeyMap ->
                val clientId = clientsToPreKeyMap.key
                val id = CryptoSessionId(CryptoUserID(userId, domain), CryptoClientId(clientId))
                createSession(clientsToPreKeyMap.value, id)
            }
        }
    }
}

expect suspend fun cryptoboxProteusClient(
    rootDir: String,
    defaultContext: CoroutineContext,
    ioContext: CoroutineContext
): ProteusClient
