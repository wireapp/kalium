/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

data class CryptoSessionId(val userId: CryptoUserID, val cryptoClientId: CryptoClientId) {
    val value: String = "${userId}_$cryptoClientId"
}

data class PreKeyCrypto(
    val id: Int,
    val encodedData: String
)

@Suppress("TooManyFunctions")
/**
 * @sample samples.cryptography.ProteusClient.basicEncryption
 */
interface ProteusClient {

    @Throws(ProteusException::class)
    fun clearLocalFiles(): Boolean

    fun needsMigration(): Boolean

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun openOrCreate()

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun openOrError()

    @Throws(ProteusException::class, CancellationException::class)
    fun getIdentity(): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    fun getLocalFingerprint(): ByteArray

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun newPreKeys(from: Int, count: Int): List<PreKeyCrypto>

    @Throws(ProteusException::class, CancellationException::class)
    suspend fun newLastPreKey(): PreKeyCrypto

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

expect class ProteusClientImpl(
    rootDir: String,
    databaseKey: ProteusDBSecret? = null,
    defaultContext: CoroutineContext,
    ioContext: CoroutineContext
) : ProteusClient
