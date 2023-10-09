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

import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
/**
 * @sample samples.cryptography.jvmInitialization
 */
actual class ProteusClientImpl actual constructor(
    rootDir: String,
    databaseKey: ProteusDBSecret?,
    defaultContext: CoroutineContext,
    ioContext: CoroutineContext
) : ProteusClient {

    private var client: ProteusClient = (databaseKey?.let {
        ProteusClientCoreCryptoImpl(rootDir, it)
    } ?: ProteusClientCryptoBoxImpl(rootDir, defaultContext = defaultContext, ioContext = ioContext))

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

    override suspend fun getLocalFingerprint(): ByteArray {
        return client.getLocalFingerprint()
    }

    override suspend fun remoteFingerPrint(sessionId: CryptoSessionId): ByteArray {
        return client.remoteFingerPrint(sessionId)
    }

    override suspend fun newPreKeys(from: Int, count: Int): List<PreKeyCrypto> {
        return client.newPreKeys(from, count)
    }

    override suspend fun newLastResortPreKey(): PreKeyCrypto {
        return client.newLastResortPreKey()
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

    override suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray> {
        return client.encryptBatched(message, sessionIds)
    }

    override suspend fun encryptWithPreKey(message: ByteArray, preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId): ByteArray {
        return client.encryptWithPreKey(message, preKeyCrypto, sessionId)
    }

    override suspend fun deleteSession(sessionId: CryptoSessionId) {
        client.deleteSession(sessionId)
    }
}
