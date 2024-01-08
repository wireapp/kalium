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

import com.wire.bots.cryptobox.CryptoBox
import com.wire.bots.cryptobox.CryptoBox.getFingerprintFromPrekey
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import java.io.File
import java.util.Base64
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
class ProteusClientCryptoBoxImpl constructor(
    rootDir: String
) : ProteusClient {

    private val path: String
    private lateinit var box: CryptoBox

    init {
        path = rootDir
    }

    fun openOrCreate() {
        val directory = File(path)
        box = wrapException {
            directory.mkdirs()
            CryptoBox.open(path)
        }
    }

    override suspend fun close() {
        if (::box.isInitialized) {
            box.close()
        }
    }

    override fun getIdentity(): ByteArray {
        return wrapException { box.identity }
    }

    override suspend fun getLocalFingerprint(): ByteArray {
        return wrapException { box.localFingerprint }
    }

    override suspend fun remoteFingerPrint(sessionId: CryptoSessionId): ByteArray {
        TODO("get session is private in Cryptobox4j")
    }

    override suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): ByteArray = wrapException {
        getFingerprintFromPrekey(toPreKey(preKey))
    }

    override suspend fun newLastResortPreKey(): PreKeyCrypto {
        return wrapException { toPreKey(box.newLastPreKey()) }
    }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> {
        return wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKeyCrypto> }
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean {
        return wrapException { box.doesSessionExist(sessionId.value) }
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        wrapException { box.encryptFromPreKeys(sessionId.value, toPreKey(preKeyCrypto), ByteArray(0)) }
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException { box.decrypt(sessionId.value, message) }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException {
            box.encryptFromSession(sessionId.value, message)
        }?.let { it } ?: throw ProteusException(null, ProteusException.Code.SESSION_NOT_FOUND)
    }

    override suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray> {
        return sessionIds.associateWith { sessionId ->
            try {
                encrypt(message, sessionId)
            } catch (e: ProteusException) {
                if (e.code == ProteusException.Code.SESSION_NOT_FOUND) {
                    ByteArray(0)
                } else {
                    throw e
                }
            }
        }.filter { it.value.isNotEmpty() }
    }

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray {
        return wrapException { box.encryptFromPreKeys(sessionId.value, toPreKey(preKeyCrypto), message) }
    }

    override suspend fun deleteSession(sessionId: CryptoSessionId) {
        // TODO Delete session
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            throw ProteusException(e.message, e.code.ordinal, e.cause)
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, e.cause)
        }
    }

    companion object {
        private fun toPreKey(preKeyCrypto: PreKeyCrypto): com.wire.bots.cryptobox.PreKey =
            com.wire.bots.cryptobox.PreKey(preKeyCrypto.id, Base64.getDecoder().decode(preKeyCrypto.encodedData))

        private fun toPreKey(preKey: com.wire.bots.cryptobox.PreKey): PreKeyCrypto =
            PreKeyCrypto(preKey.id, Base64.getEncoder().encodeToString(preKey.data))
    }

}

actual suspend fun cryptoboxProteusClient(
    rootDir: String,
    defaultContext: CoroutineContext,
    ioContext: CoroutineContext
): ProteusClient {
    val proteusClient = ProteusClientCryptoBoxImpl(rootDir)
    proteusClient.openOrCreate()
    return proteusClient
}
