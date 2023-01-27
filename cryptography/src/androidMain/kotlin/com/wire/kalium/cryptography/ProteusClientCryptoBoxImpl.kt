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

import android.util.Base64
import com.wire.cryptobox.CryptoBox
import com.wire.cryptobox.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import java.io.File

@Suppress("TooManyFunctions")
class ProteusClientCryptoBoxImpl constructor(rootDir: String) : ProteusClient {

    private val path: String
    private lateinit var box: CryptoBox

    init {
        path = rootDir
    }

    override fun clearLocalFiles(): Boolean {
        box.close()
        return File(path).deleteRecursively()
    }

    override fun needsMigration(): Boolean {
        return false
    }

    override suspend fun openOrCreate() {
        val directory = File(path)
        box = wrapException {
            directory.mkdirs()
            CryptoBox.open(path)
        }
    }

    override suspend fun openOrError() {
        val directory = File(path)
        if (directory.exists()) {
            box = wrapException {
                directory.mkdirs()
                CryptoBox.open(path)
            }
        } else {
            throw ProteusException("Local files were not found", ProteusException.Code.LOCAL_FILES_NOT_FOUND)
        }
    }

    override fun getIdentity(): ByteArray {
        return wrapException { box.copyIdentity() }
    }

    override fun getLocalFingerprint(): ByteArray {
        return wrapException { box.localFingerprint }
    }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> {
        return wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKeyCrypto> }
    }

    override fun newLastPreKey(): PreKeyCrypto {
        return wrapException { toPreKey(box.newLastPreKey()) }
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean {
        return try {
            box.getSession(sessionId.value)
            true
        } catch (e: CryptoException) {
            if (e.code == CryptoException.Code.SESSION_NOT_FOUND) {
                false
            } else {
                throw e
            }
        }
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        wrapException { box.initSessionFromPreKey(sessionId.value, toPreKey(preKeyCrypto)) }
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        val session = box.tryGetSession(sessionId.value)

        return wrapException {
            if (session != null) {
                val decryptedMessage = session.decrypt(message)
                session.save()
                decryptedMessage
            } else {
                val result = box.initSessionFromMessage(sessionId.value, message)
                result.session.save()
                result.message
            }
        }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException {
            val session = box.getSession(sessionId.value)
            val encryptedMessage = session.encrypt(message)
            session.save()
            encryptedMessage
        }
    }

    override suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray> {
        return sessionIds.associateWith { sessionId ->
            encrypt(message, sessionId)
        }
    }

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray {
        return wrapException {
            val session = box.initSessionFromPreKey(sessionId.value, toPreKey(preKeyCrypto))
            val encryptedMessage = session.encrypt(message)
            session.save()
            encryptedMessage
        }
    }

    override fun deleteSession(sessionId: CryptoSessionId) {
        wrapException {
            box.deleteSession(sessionId.value)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            throw ProteusException(e.message, fromCryptoException(e))
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
        }
    }

    @Suppress("ComplexMethod")
    private fun fromCryptoException(e: CryptoException): ProteusException.Code {
        return when (e.code) {
            CryptoException.Code.SESSION_NOT_FOUND -> ProteusException.Code.SESSION_NOT_FOUND
            CryptoException.Code.REMOTE_IDENTITY_CHANGED -> ProteusException.Code.REMOTE_IDENTITY_CHANGED
            CryptoException.Code.INVALID_SIGNATURE -> ProteusException.Code.INVALID_SIGNATURE
            CryptoException.Code.INVALID_MESSAGE -> ProteusException.Code.INVALID_MESSAGE
            CryptoException.Code.DUPLICATE_MESSAGE -> ProteusException.Code.DUPLICATE_MESSAGE
            CryptoException.Code.TOO_DISTANT_FUTURE -> ProteusException.Code.TOO_DISTANT_FUTURE
            CryptoException.Code.OUTDATED_MESSAGE -> ProteusException.Code.OUTDATED_MESSAGE
            CryptoException.Code.DECODE_ERROR -> ProteusException.Code.DECODE_ERROR
            CryptoException.Code.STORAGE_ERROR -> ProteusException.Code.STORAGE_ERROR
            CryptoException.Code.IDENTITY_ERROR -> ProteusException.Code.IDENTITY_ERROR
            CryptoException.Code.PREKEY_NOT_FOUND -> ProteusException.Code.PREKEY_NOT_FOUND
            CryptoException.Code.PANIC -> ProteusException.Code.PANIC
            CryptoException.Code.INIT_ERROR -> ProteusException.Code.UNKNOWN_ERROR
            CryptoException.Code.DEGENERATED_KEY -> ProteusException.Code.UNKNOWN_ERROR
            CryptoException.Code.INVALID_STRING -> ProteusException.Code.UNKNOWN_ERROR
            CryptoException.Code.UNKNOWN_ERROR -> ProteusException.Code.UNKNOWN_ERROR
            else -> ProteusException.Code.UNKNOWN_ERROR
        }
    }

    companion object {
        private fun toPreKey(preKey: PreKeyCrypto): com.wire.cryptobox.PreKey =
            com.wire.cryptobox.PreKey(preKey.id, Base64.decode(preKey.encodedData, Base64.NO_WRAP))

        private fun toPreKey(preKey: com.wire.cryptobox.PreKey): PreKeyCrypto =
            PreKeyCrypto(preKey.id, Base64.encodeToString(preKey.data, Base64.NO_WRAP))
    }

}
