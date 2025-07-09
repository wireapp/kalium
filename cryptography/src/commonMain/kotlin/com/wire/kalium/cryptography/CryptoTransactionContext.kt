/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import io.mockative.Mockable

/**
 * Provides a composite context that can be used inside a crypto transaction.
 *
 * This context encapsulates access to both cryptographic engines:
 * - [mls]: The MLS cryptography context (can be `null` if not supported yet).
 * - [proteus]: The Proteus cryptography context.
 *
 * It is typically passed into blocks executed by [CryptoTransactionProvider.transaction].
 */
@Mockable
interface CryptoTransactionContext {
    val mls: MlsCoreCryptoContext?
    val proteus: ProteusCoreCryptoContext
}

// TODO KBX implement mls in next PR
@Mockable
interface MlsCoreCryptoContext {
//    suspend fun decryptMessage(
//        groupId: ByteArray,
//        message: ByteArray,
//        messageInstant: Instant
//    ): DecryptedMessageBundle
//
//    suspend fun isGroupOutOfSync(groupID: ByteArray, currentEpoch: ULong): Boolean
//    suspend fun joinByExternalCommit(publicGroupState: ByteArray): WelcomeBundle
//
//    /**
//     * Query if a conversation exists
//     *
//     * @param groupId MLS group ID provided by BE
//     *
//     * @return true if conversation exists in store
//     */
//    suspend fun conversationExists(groupId: MLSGroupId): Boolean
//
//    suspend fun processWelcomeMessage(message: WelcomeMessage): WelcomeBundle
}

@Mockable
interface ProteusCoreCryptoContext {
    suspend fun <T : Any> decryptMessage(
        sessionId: CryptoSessionId,
        message: ByteArray,
        handleDecryptedMessage: suspend (decryptedMessage: ByteArray) -> T
    ): T

    suspend fun getLocalFingerprint(): ByteArray
    suspend fun remoteFingerPrint(sessionId: CryptoSessionId): ByteArray
    suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): ByteArray
    suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto>
    suspend fun newLastResortPreKey(): PreKeyCrypto
    suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean
    suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId)

    suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray
    suspend fun encryptBatched(
        message: ByteArray,
        sessionIds: List<CryptoSessionId>
    ): Map<CryptoSessionId, ByteArray>

    suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray

    suspend fun deleteSession(sessionId: CryptoSessionId)
}

suspend fun ProteusCoreCryptoContext.createSessions(
    preKeysCrypto: Map<String, Map<String, Map<String, PreKeyCrypto>>>
) {
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
