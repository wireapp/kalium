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
import com.wire.crypto.client.toByteArray
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.cryptography.exceptions.ProteusStorageMigrationException
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
        return wrapException { coreCrypto.proteusFingerprint().toByteArray() }
    }

    override suspend fun remoteFingerPrint(sessionId: CryptoSessionId): ByteArray = wrapException {
        coreCrypto.proteusFingerprintRemote(sessionId.value).toByteArray()
    }

    override suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): ByteArray = wrapException {
        coreCrypto.proteusFingerprintPrekeybundle(preKey.encodedData.decodeBase64Bytes()).toByteArray()
    }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> {
        return wrapException {
            from.until(from + count).map {
                toPreKey(it, coreCrypto.proteusNewPrekey(it.toUShort()))
            } as ArrayList<PreKeyCrypto>
        }
    }

    override suspend fun newLastResortPreKey(): PreKeyCrypto {
        return wrapException { toPreKey(coreCrypto.proteusLastResortPrekeyId().toInt(), coreCrypto.proteusLastResortPrekey()) }
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean = mutex.withLock {
        if (existingSessionsCache.contains(sessionId)) {
            return@withLock true
        }
        wrapException {
            coreCrypto.proteusSessionExists(sessionId.value)
        }.also { exists ->
            if (exists) {
                existingSessionsCache.add(sessionId)
            }
        }
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        wrapException { coreCrypto.proteusSessionFromPrekey(sessionId.value, preKeyCrypto.encodedData.decodeBase64Bytes()) }
    }

    override suspend fun <T : Any> decrypt(
        message: ByteArray,
        sessionId: CryptoSessionId,
        handleDecryptedMessage: suspend (decryptedMessage: ByteArray) -> T
    ): T {
        val sessionExists = doesSessionExist(sessionId)

        return wrapException {
            val decryptedMessage = if (sessionExists) {
                coreCrypto.proteusDecrypt(sessionId.value, message)
            } else {
                coreCrypto.proteusSessionFromMessage(sessionId.value, message)
            }
            handleDecryptedMessage(decryptedMessage)
        }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException {
            val encryptedMessage = coreCrypto.proteusEncrypt(sessionId.value, message)
            coreCrypto.proteusSessionSave(sessionId.value)
            encryptedMessage
        }
    }

    override suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray> {
        return wrapException {
            coreCrypto.proteusEncryptBatched(sessionIds.map { it.value }, message).mapNotNull { entry ->
                CryptoSessionId.fromEncodedString(entry.key)?.let { sessionId ->
                    sessionId to entry.value
                }
            }.toMap()
        }
    }

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray {
        return wrapException {
            coreCrypto.proteusSessionFromPrekey(sessionId.value, preKeyCrypto.encodedData.decodeBase64Bytes())
            val encryptedMessage = coreCrypto.proteusEncrypt(sessionId.value, message)
            coreCrypto.proteusSessionSave(sessionId.value)
            encryptedMessage
        }
    }

    override suspend fun deleteSession(sessionId: CryptoSessionId) = mutex.withLock {
        existingSessionsCache.remove(sessionId)
        wrapException {
            coreCrypto.proteusSessionDelete(sessionId.value)
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private inline fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CoreCryptoException.Proteus) {
            throw ProteusException(
                message = e.message,
                code = mapProteusExceptionToErrorCode(e.v1),
                intCode = mapProteusExceptionToRawIntErrorCode(e.v1),
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

        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()
        fun toPreKey(id: Int, data: ByteArray): PreKeyCrypto =
            PreKeyCrypto(id, data.encodeBase64())

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
                coreCrypto.proteusInit()
                return ProteusClientCoreCryptoImpl(coreCrypto)
            } catch (exception: ProteusStorageMigrationException) {
                throw exception
            } catch (e: CoreCryptoException.Proteus) {
                throw ProteusException(
                    message = e.message,
                    code = mapProteusExceptionToErrorCode(e.v1),
                    intCode = mapProteusExceptionToRawIntErrorCode(e.v1),
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
                    coreCrypto.proteusCryptoboxMigrate(rootDir)
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
                is ProteusExceptionNative.Other -> ProteusException.fromProteusCode(proteusException.v1.toInt())
            }
        }

        @Suppress("MagicNumber")
        private fun mapProteusExceptionToRawIntErrorCode(proteusException: ProteusExceptionNative): Int {
            return when (proteusException) {
                is ProteusExceptionNative.SessionNotFound -> 102
                is ProteusExceptionNative.DuplicateMessage -> 209
                is ProteusExceptionNative.RemoteIdentityChanged -> 204
                is ProteusExceptionNative.Other -> proteusException.v1.toInt()
            }
        }
    }
}
