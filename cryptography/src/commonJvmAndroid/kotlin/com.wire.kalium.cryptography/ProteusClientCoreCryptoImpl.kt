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

import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.cryptography.exceptions.ProteusStorageMigrationException
import com.wire.kalium.cryptography.utils.toCrypto
import com.wire.kalium.cryptography.utils.toCryptography
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import com.wire.crypto.ProteusException as ProteusExceptionNative

@Suppress("TooManyFunctions")
class ProteusClientCoreCryptoImpl private constructor(
    private val coreCrypto: CoreCrypto,
) : ProteusClient {

    private val mutex = Mutex()
    private val existingSessionsCache = mutableSetOf<CryptoSessionId>()

    override suspend fun close() {
        coreCrypto.close()
    }

    override fun getIdentity(): ByteArray {
        return ByteArray(0)
    }

    override suspend fun getLocalFingerprint(): ByteArray {
        return wrapException {
            coreCrypto.transaction {
                it.proteusGetLocalFingerprint()
            }
        }
    }

    override suspend fun remoteFingerPrint(sessionId: CryptoSessionId): ByteArray = wrapException {
        coreCrypto.transaction {
            it.proteusGetRemoteFingerprint(sessionId.value)
        }
    }

    override suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): ByteArray = wrapException {
        coreCrypto.transaction {
            it.proteusGetPrekeyFingerprint(preKey.encodedData.decodeBase64Bytes())
        }
    }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> {
        return wrapException {
            coreCrypto.transaction { crypto ->
                crypto.proteusNewPreKeys(from, count).map {
                    PreKeyCrypto(it.id.toInt(), it.data.encodeBase64())
                } as ArrayList<PreKeyCrypto>
            }
        }
    }

    override suspend fun newLastResortPreKey(): PreKeyCrypto {
        return wrapException {
            coreCrypto.transaction { context ->
                context.proteusNewLastPreKey().toCryptography()
            }
        }
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean = mutex.withLock {
        if (existingSessionsCache.contains(sessionId)) {
            return@withLock true
        }
        wrapException {
            coreCrypto.transaction {
                it.proteusDoesSessionExist(sessionId.value)
            }
        }.also { exists ->
            if (exists) {
                existingSessionsCache.add(sessionId)
            }
        }
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        wrapException {
            coreCrypto.transaction {
                it.proteusCreateSession(preKeyCrypto.toCrypto(), sessionId.value)
            }
        }
    }

    override suspend fun <T : Any> decrypt(
        message: ByteArray,
        sessionId: CryptoSessionId,
        handleDecryptedMessage: suspend (decryptedMessage: ByteArray) -> T
    ): T {

        return wrapException {
            coreCrypto.transaction {
                handleDecryptedMessage(it.proteusDecrypt(message, sessionId.value))
            }
        }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException {
            coreCrypto.transaction {
                it.proteusEncrypt(message, sessionId.value)
            }
        }
    }

    override suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray> {
        return wrapException {
            coreCrypto.transaction {
                it.proteusEncryptBatched(sessionIds.map { sessionId -> sessionId.value }, message).mapNotNull { entry ->
                    CryptoSessionId.fromEncodedString(entry.key)?.let { sessionId ->
                        sessionId to entry.value
                    }
                }.toMap()
            }
        }
    }

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray {
        return wrapException {
            coreCrypto.transaction {
                it.proteusCreateSession(preKeyCrypto.toCrypto(), sessionId.value)
                it.proteusEncrypt(message, sessionId.value)
            }
        }
    }

    override suspend fun deleteSession(sessionId: CryptoSessionId) = mutex.withLock {
        existingSessionsCache.remove(sessionId)
        wrapException {
            coreCrypto.transaction {
                it.proteusDeleteSession(sessionId.value)
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

    @OptIn(ExperimentalUnsignedTypes::class)
    companion object {

        val CRYPTO_BOX_FILES = listOf("identities", "prekeys", "sessions", "version")

        private fun cryptoBoxFilesExists(rootDir: File): Boolean =
            CRYPTO_BOX_FILES.any {
                rootDir.resolve(it).exists()
            }

        private fun deleteCryptoBoxFiles(rootDir: String): Boolean =
            CRYPTO_BOX_FILES.fold(true) { acc, file ->
                acc && File(rootDir).resolve(file).deleteRecursively()
            }

        @Suppress("TooGenericExceptionCaught", "ThrowsCount")
        suspend operator fun invoke(coreCrypto: CoreCrypto, rootDir: String): ProteusClientCoreCryptoImpl {
            try {
                migrateFromCryptoBoxIfNecessary(coreCrypto, rootDir)
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
        private suspend fun migrateFromCryptoBoxIfNecessary(coreCrypto: CoreCrypto, rootDir: String) {
            try {
                if (cryptoBoxFilesExists(File(rootDir))) {
                    kaliumLogger.i("migrating from crypto box at: $rootDir")
                    coreCrypto.transaction {
                        it.proteusCryptoboxMigrate(rootDir)
                    }
                    kaliumLogger.i("migration successful")

                    if (deleteCryptoBoxFiles(rootDir)) {
                        kaliumLogger.i("successfully deleted old crypto box files")
                    } else {
                        kaliumLogger.e("Failed to deleted old crypto box files at $rootDir")
                    }
                }
            } catch (exception: Exception) {
                kaliumLogger.e("Failed to migrate from crypto box to core crypto, exception: $exception")
                throw ProteusStorageMigrationException("Failed to migrate from crypto box at $rootDir", exception)
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
