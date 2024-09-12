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

import android.util.Base64
import com.wire.cryptobox.CryptoBox
import com.wire.cryptobox.CryptoBox.getFingerprintFromPrekey
import com.wire.cryptobox.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext
import com.wire.cryptobox.PreKey as CryptoBoxPreKey

@Suppress("TooManyFunctions")
class ProteusClientCryptoBoxImpl constructor(
    rootDir: String,
    private val defaultContext: CoroutineContext,
    private val ioContext: CoroutineContext
) : ProteusClient {

    private val path: String
    private lateinit var box: CryptoBox

    private val lock = Mutex()

    init {
        path = rootDir
    }

    override suspend fun close() {
        if (::box.isInitialized) {
            box.close()
        }
    }

    override suspend fun remoteFingerPrint(sessionId: CryptoSessionId): ByteArray = withContext(defaultContext) {
        wrapException { box.getSession(sessionId.value).remoteFingerprint }
    }

    override suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): ByteArray =
        withContext(defaultContext) {
            val cryptoBoxPreKey = CryptoBoxPreKey(preKey.id, preKey.encodedData.decodeBase64Bytes())
            getFingerprintFromPrekey(cryptoBoxPreKey)
        }

    /**
     * Create the crypto files if missing and call box.open
     * this must be called only one time
     */
    fun openOrCreate() {
        if (!this::box.isInitialized) {
            val directory = File(path)
            box = wrapException {
                directory.mkdirs()
                CryptoBox.open(path)
            }
        }
    }

    override fun getIdentity(): ByteArray = wrapException { box.copyIdentity() }

    override suspend fun getLocalFingerprint(): ByteArray = wrapException { box.localFingerprint }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> = lock.withLock {
        withContext(defaultContext) {
            wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKeyCrypto> }
        }
    }

    override suspend fun newLastResortPreKey(): PreKeyCrypto = lock.withLock {
        withContext(defaultContext) {
            wrapException { toPreKey(box.newLastPreKey()) }
        }
    }

    // TODO: this function calls the native function session_load which does open the session file and
    //  parse it content we can consider changing it to a simple check if the session file exists on the local storage or not
    //  or rename it to doesValidSessionExist
    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean = lock.withLock {
        withContext(ioContext) {
            box.tryGetSession(sessionId.value)?.let { true } ?: false
        }
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        lock.withLock {
            withContext(ioContext) {
                wrapException { box.initSessionFromPreKey(sessionId.value, toPreKey(preKeyCrypto)) }
            }
        }
    }

    override suspend fun <T : Any> decrypt(
        message: ByteArray,
        sessionId: CryptoSessionId,
        handleDecryptedMessage: suspend (decryptedMessage: ByteArray) -> T
    ): T = lock.withLock {
        withContext(defaultContext) {
            val session = box.tryGetSession(sessionId.value)
            wrapException {
                if (session != null) {
                    val decryptedMessage = session.decrypt(message)
                    handleDecryptedMessage(decryptedMessage).also {
                        session.save()
                    }
                } else {
                    val result = box.initSessionFromMessage(sessionId.value, message)
                    handleDecryptedMessage(result.message).also {
                        result.session.save()
                    }
                }
            }
        }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray = withContext(defaultContext) {
        lock.withLock {
            withContext(defaultContext) {
                wrapException {
                    val session = box.getSession(sessionId.value)
                    val encryptedMessage = session.encrypt(message)
                    session.save()
                    encryptedMessage
                }
            }
        }
    }

    override suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray> =
        sessionIds.associateWith { sessionId ->
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

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray = lock.withLock {
        withContext(defaultContext) {
            wrapException {
                val session = box.initSessionFromPreKey(sessionId.value, toPreKey(preKeyCrypto))
                val encryptedMessage = session.encrypt(message)
                session.save()
                encryptedMessage
            }
        }
    }

    override suspend fun deleteSession(sessionId: CryptoSessionId) = lock.withLock {
        withContext(ioContext) {
            wrapException {
                box.deleteSession(sessionId.value)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            throw ProteusException(e.message, fromCryptoException(e), e.cause)
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, e.cause)
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
            null -> ProteusException.Code.UNKNOWN_ERROR
        }
    }

    companion object {
        private fun toPreKey(preKey: PreKeyCrypto): com.wire.cryptobox.PreKey =
            com.wire.cryptobox.PreKey(preKey.id, Base64.decode(preKey.encodedData, Base64.NO_WRAP))

        private fun toPreKey(preKey: com.wire.cryptobox.PreKey): PreKeyCrypto =
            PreKeyCrypto(preKey.id, Base64.encodeToString(preKey.data, Base64.NO_WRAP))
    }
}

actual suspend fun cryptoboxProteusClient(
    rootDir: String,
    defaultContext: CoroutineContext,
    ioContext: CoroutineContext
): ProteusClient {
    val proteusClient = ProteusClientCryptoBoxImpl(rootDir, defaultContext, ioContext)
    proteusClient.openOrCreate()
    return proteusClient
}
