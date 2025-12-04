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

import com.wire.crypto.CoreCryptoClient
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.ProteusAutoPrekeyBundle
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.cryptography.exceptions.ProteusStorageMigrationException
import com.wire.kalium.cryptography.utils.toCrypto
import com.wire.kalium.cryptography.utils.toCryptography
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import com.wire.crypto.ProteusException as ProteusExceptionNative

@Suppress("TooManyFunctions")
class ProteusClientCoreCryptoImpl private constructor(
    private val coreCrypto: CoreCryptoClient,
) : ProteusClient {

    private val mutex = Mutex()
    private val existingSessionsCache = mutableSetOf<CryptoSessionId>()

    override suspend fun close() {
        coreCrypto.close()
    }

    override suspend fun <R> transaction(
        name: String,
        block: suspend (context: ProteusCoreCryptoContext) -> R
    ): R {
        return wrapException {
            coreCrypto.transaction { coreContext ->
                block(proteusCoreCryptoContext(coreContext))
            }
        }
    }

    private fun proteusCoreCryptoContext(coreContext: CoreCryptoContext) = object : ProteusCoreCryptoContext {
        override suspend fun getLocalFingerprint(): String {
            return coreContext.proteusFingerprint()
        }

        override suspend fun remoteFingerPrint(sessionId: CryptoSessionId): String {
            return coreContext.proteusFingerprintRemote(sessionId.value)
        }

        override suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): String {
            return coreContext.proteusFingerprintPrekeybundle(Base64.decode(preKey.pkb))
        }

        override suspend fun newLastResortPreKey(): PreKeyCrypto {
            val id = coreContext.proteusLastResortPrekeyId()
            val pkb = coreContext.proteusLastResortPrekey()
            return ProteusAutoPrekeyBundle(id, pkb).toCryptography()
        }

        override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean = mutex.withLock {
            if (existingSessionsCache.contains(sessionId)) {
                return@withLock true
            }

            coreContext.proteusSessionExists(sessionId.value)
                .also { exists ->
                    if (exists) {
                        existingSessionsCache.add(sessionId)
                    }
                }
        }

        override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
            return coreContext.proteusSessionFromPrekey(sessionId.value, preKeyCrypto.toCrypto().pkb)
        }

        override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
            return coreContext.proteusEncrypt(sessionId.value, message)
        }

        override suspend fun encryptBatched(
            message: ByteArray,
            sessionIds: List<CryptoSessionId>
        ): Map<CryptoSessionId, ByteArray> {
            return coreContext.proteusEncryptBatched(sessionIds.map { sessionId -> sessionId.value }, message)
                .mapNotNull { entry ->
                    CryptoSessionId.fromEncodedString(entry.key)?.let { sessionId ->
                        sessionId to entry.value
                    }
                }.toMap()
        }

        override suspend fun encryptWithPreKey(
            message: ByteArray,
            preKeyCrypto: PreKeyCrypto,
            sessionId: CryptoSessionId
        ): ByteArray {
            coreContext.proteusSessionFromPrekey(sessionId.value, preKeyCrypto.toCrypto().pkb)
            return coreContext.proteusEncrypt(sessionId.value, message)
        }

        override suspend fun deleteSession(sessionId: CryptoSessionId) {
            existingSessionsCache.remove(sessionId)
            coreContext.proteusSessionDelete(sessionId.value)
        }

        override suspend fun <T : Any> decryptMessage(
            sessionId: CryptoSessionId,
            message: ByteArray,
            handleDecryptedMessage: suspend (decryptedMessage: ByteArray) -> T
        ): T {
            val decrypted = coreContext.proteusDecryptSafe(sessionId.value, message)
            return handleDecryptedMessage(decrypted)
        }
    }

    override suspend fun newPreKeys(from: Int, count: Int): List<PreKeyCrypto> {
        return wrapException {
            coreCrypto.transaction { crypto ->
                from.until(from + count).map {
                    val pkb = crypto.proteusNewPrekey(it.toUShort())
                    ProteusAutoPrekeyBundle(it.toUShort(), pkb).toCryptography()
                }
            }
        }
    }

    override suspend fun newLastResortPreKey(): PreKeyCrypto {
        return wrapException {
            coreCrypto.transaction { context ->
                val id = context.proteusLastResortPrekeyId()
                val pkb = context.proteusLastResortPrekey()
                ProteusAutoPrekeyBundle(id, pkb).toCryptography()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private inline fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CoreCryptoException.Proteus) {
            throw ProteusException(
                message = e.message,
                code = mapProteusExceptionToErrorCode(e.exception),
                intCode = mapProteusExceptionToRawIntErrorCode(e.exception),
                cause = e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, null, e)
        }
    }

    companion object {

        val CRYPTO_BOX_FILES = listOf("identities", "prekeys", "sessions", "version")

        private fun cryptoBoxFilesExists(rootDir: String): Boolean =
            CRYPTO_BOX_FILES.any { file ->
                fileExists("$rootDir/$file")
            }

        private fun deleteCryptoBoxFiles(rootDir: String): Boolean =
            CRYPTO_BOX_FILES.fold(true) { acc, file ->
                val path = "$rootDir/$file"
                if (fileExists(path)) {
                    acc && deleteFile(path)
                } else {
                    acc
                }
            }

        @Suppress("TooGenericExceptionCaught", "ThrowsCount")
        suspend operator fun invoke(coreCrypto: CoreCryptoClient, rootDir: String): ProteusClientCoreCryptoImpl {
            try {
                deleteCryptoBoxIfNecessary(rootDir)
                coreCrypto.transaction {
                    it.proteusInit()
                }
                return ProteusClientCoreCryptoImpl(coreCrypto)
            } catch (exception: ProteusStorageMigrationException) {
                throw exception
            } catch (e: CoreCryptoException.Proteus) {
                throw ProteusException(
                    message = e.message,
                    code = mapProteusExceptionToErrorCode(e.exception),
                    intCode = mapProteusExceptionToRawIntErrorCode(e.exception),
                    cause = e.cause
                )
            } catch (e: Exception) {
                throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, null, e.cause)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun deleteCryptoBoxIfNecessary(rootDir: String) {
            try {
                if (cryptoBoxFilesExists(rootDir)) {
                    if (deleteCryptoBoxFiles(rootDir)) {
                        kaliumLogger.i("successfully deleted old crypto box files")
                    } else {
                        kaliumLogger.e("Failed to deleted old crypto box files at $rootDir")
                    }
                }
            } catch (exception: Exception) {
                kaliumLogger.e("Failed to delete crypto box, exception: $exception")
            }
        }

        private fun mapProteusExceptionToErrorCode(proteusException: ProteusExceptionNative): ProteusException.Code {
            return when (proteusException) {
                is ProteusExceptionNative.SessionNotFound -> ProteusException.Code.SESSION_NOT_FOUND
                is ProteusExceptionNative.DuplicateMessage -> ProteusException.Code.DUPLICATE_MESSAGE
                is ProteusExceptionNative.RemoteIdentityChanged -> ProteusException.Code.REMOTE_IDENTITY_CHANGED
                is ProteusExceptionNative.Other -> ProteusException.fromProteusCode(proteusException.errorCode.toInt())
            }
        }

        @Suppress("MagicNumber")
        private fun mapProteusExceptionToRawIntErrorCode(proteusException: ProteusExceptionNative): Int {
            return when (proteusException) {
                is ProteusExceptionNative.SessionNotFound -> 102
                is ProteusExceptionNative.DuplicateMessage -> 209
                is ProteusExceptionNative.RemoteIdentityChanged -> 204
                is ProteusExceptionNative.Other -> proteusException.errorCode.toInt()
            }
        }
    }
}
