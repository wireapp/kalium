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
import com.wire.crypto.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.toByteArray
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.URLByAppendingPathComponent

@Suppress("TooManyFunctions")
class ProteusClientCoreCryptoImpl private constructor(private val coreCrypto: CoreCrypto) : ProteusClient {
    @Suppress("EmptyFunctionBlock")
    override suspend fun close() {}

    override fun getIdentity(): ByteArray {
        return ByteArray(0)
    }

    override suspend fun getLocalFingerprint(): ByteArray {
        return wrapException { coreCrypto.proteusFingerprint().toByteArray() }
    }

    override suspend fun remoteFingerPrint(sessionId: CryptoSessionId): ByteArray {
        return wrapException { coreCrypto.proteusFingerprintRemote(sessionId.value).toByteArray() }
    }

    override suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): ByteArray {
        // TODO this is a hack, we need to expose the fingerprint from the core
        return "".toByteArray()
    }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> {
        return wrapException {
            from.until(from + count).map {
                toPreKey(it, toByteArray(coreCrypto.proteusNewPrekey(it.toUShort())))
            } as ArrayList<PreKeyCrypto>
        }
    }

    override suspend fun newLastResortPreKey(): PreKeyCrypto {
        return wrapException { toPreKey(UShort.MAX_VALUE.toInt(), toByteArray(coreCrypto.proteusNewPrekey(UShort.MAX_VALUE))) }
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean {
        return wrapException {
            coreCrypto.proteusSessionExists(sessionId.value)
        }
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        wrapException { coreCrypto.proteusSessionFromPrekey(sessionId.value, toUByteList(preKeyCrypto.encodedData.decodeBase64Bytes())) }
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        val sessionExists = doesSessionExist(sessionId)

        return wrapException {
            if (sessionExists) {
                val decryptedMessage = toByteArray(coreCrypto.proteusDecrypt(sessionId.value, toUByteList(message)))
                coreCrypto.proteusSessionSave(sessionId.value)
                decryptedMessage
            } else {
                val decryptedMessage = toByteArray(coreCrypto.proteusSessionFromMessage(sessionId.value, toUByteList(message)))
                coreCrypto.proteusSessionSave(sessionId.value)
                decryptedMessage
            }
        }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException {
            val encryptedMessage = toByteArray(coreCrypto.proteusEncrypt(sessionId.value, toUByteList(message)))
            coreCrypto.proteusSessionSave(sessionId.value)
            encryptedMessage
        }
    }

    override suspend fun encryptBatched(message: ByteArray, sessionIds: List<CryptoSessionId>): Map<CryptoSessionId, ByteArray> {
        return wrapException {
            coreCrypto.proteusEncryptBatched(sessionIds.map { it.value }, toUByteList((message))).mapNotNull { entry ->
                CryptoSessionId.fromEncodedString(entry.key)?.let { sessionId ->
                    sessionId to toByteArray(entry.value)
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
            coreCrypto.proteusSessionFromPrekey(sessionId.value, toUByteList(preKeyCrypto.encodedData.decodeBase64Bytes()))
            val encryptedMessage = toByteArray(coreCrypto.proteusEncrypt(sessionId.value, toUByteList(message)))
            coreCrypto.proteusSessionSave(sessionId.value)
            encryptedMessage
        }
    }

    override suspend fun deleteSession(sessionId: CryptoSessionId) {
        wrapException {
            coreCrypto.proteusSessionDelete(sessionId.value)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            // TODO underlying proteus error is not exposed atm
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
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

        private fun cryptoBoxFilesExists(rootDir: String): Boolean =
            CRYPTO_BOX_FILES.any {
                NSURL.fileURLWithPath(rootDir).URLByAppendingPathComponent(it)?.checkResourceIsReachableAndReturnError(null) ?: false
            }

        private fun deleteCryptoBoxFiles(rootDir: String): Boolean =
            CRYPTO_BOX_FILES.fold(true) { acc, file ->
                val deleted = NSURL.fileURLWithPath(rootDir).URLByAppendingPathComponent(file)?.let {
                    NSFileManager.defaultManager.removeItemAtURL(it, null)
                } ?: false

                acc && deleted
            }

        private fun migrateFromCryptoBoxIfNecessary(coreCrypto: CoreCrypto, rootDir: String) {
            if (cryptoBoxFilesExists(rootDir)) {
                migrateFromCryptoBox(coreCrypto, rootDir)
            }
        }

        private fun migrateFromCryptoBox(coreCrypto: CoreCrypto, rootDir: String) {
            kaliumLogger.i("migrating from crypto box at: $rootDir")
            coreCrypto.proteusCryptoboxMigrate(rootDir)
            kaliumLogger.i("migration successful")

            if (deleteCryptoBoxFiles(rootDir)) {
                kaliumLogger.i("successfully deleted old crypto box files")
            } else {
                kaliumLogger.e("Failed to deleted old crypto box files at $rootDir")
            }
        }

        @Suppress("TooGenericExceptionCaught")
        operator fun invoke(coreCrypto: CoreCrypto, rootDir: String): ProteusClientCoreCryptoImpl {
            try {
                migrateFromCryptoBoxIfNecessary(coreCrypto, rootDir)
                coreCrypto.proteusInit()
                return ProteusClientCoreCryptoImpl(coreCrypto)
            } catch (e: CryptoException) {
                throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, e.cause)
            } catch (e: Exception) {
                throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, e.cause)
            }
        }
    }
}
