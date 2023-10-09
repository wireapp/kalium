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

import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.client.Ciphersuites
import com.wire.crypto.coreCryptoDeferredInit
import com.wire.kalium.cryptography.exceptions.ProteusException
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

@Suppress("TooManyFunctions")
class ProteusClientCoreCryptoImpl internal constructor(
    private val rootDir: String,
    private val databaseKey: ProteusDBSecret
) : ProteusClient {

    private val path: String = "$rootDir/$KEYSTORE_NAME"
    private lateinit var coreCrypto: CoreCrypto

    override fun clearLocalFiles(): Boolean {
        if (::coreCrypto.isInitialized) {
            coreCrypto.close()
        }
        return File(path).deleteRecursively()
    }

    override fun needsMigration(): Boolean {
        return cryptoBoxFilesExists()
    }

    override suspend fun openOrCreate() {
        wrapException {
            File(rootDir).mkdirs()
            runBlocking {
                coreCrypto = coreCryptoDeferredInit(
                    path,
                    databaseKey.value,
                    Ciphersuites.DEFAULT.lower(),
                    null
                )
                migrateFromCryptoBoxIfNecessary(coreCrypto)
                coreCrypto.proteusInit()
                coreCrypto
            }
        }
    }

    override suspend fun openOrError() {
        val directory = File(rootDir)
        if (directory.exists()) {
            wrapException {
                runBlocking {
                    coreCrypto = coreCryptoDeferredInit(
                        path,
                        databaseKey.value,
                        Ciphersuites.DEFAULT.lower(),
                        null
                    )
                    migrateFromCryptoBoxIfNecessary(coreCrypto)
                    coreCrypto.proteusInit()
                }
            }
        } else {
            throw ProteusException(
                "Local files were not found",
                ProteusException.Code.LOCAL_FILES_NOT_FOUND,
                FileNotFoundException()
            )
        }
    }

    private fun cryptoBoxFilesExists(): Boolean =
        CRYPTO_BOX_FILES.any {
            File(rootDir).resolve(it).exists()
        }

    private fun deleteCryptoBoxFiles(): Boolean =
        CRYPTO_BOX_FILES.fold(true) { acc, file ->
            acc && File(rootDir).resolve(file).deleteRecursively()
        }

    private suspend fun migrateFromCryptoBoxIfNecessary(coreCrypto: CoreCrypto) {
        if (cryptoBoxFilesExists()) {
            migrateFromCryptoBox(coreCrypto)
        }
    }

    private suspend fun migrateFromCryptoBox(coreCrypto: CoreCrypto) {
        kaliumLogger.i("migrating from crypto box at: $rootDir")
        coreCrypto.proteusCryptoboxMigrate(rootDir)
        kaliumLogger.i("migration successful")

        if (deleteCryptoBoxFiles()) {
            kaliumLogger.i("successfully deleted old crypto box files")
        } else {
            kaliumLogger.e("Failed to deleted old crypto box files at $rootDir")
        }
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

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean {
        return wrapException {
            coreCrypto.proteusSessionExists(sessionId.value)
        }
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        wrapException { coreCrypto.proteusSessionFromPrekey(sessionId.value, preKeyCrypto.encodedData.decodeBase64Bytes()) }
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        val sessionExists = doesSessionExist(sessionId)

        return wrapException {
            if (sessionExists) {
                val decryptedMessage = coreCrypto.proteusDecrypt(sessionId.value, message)
                coreCrypto.proteusSessionSave(sessionId.value)
                decryptedMessage
            } else {
                val decryptedMessage = coreCrypto.proteusSessionFromMessage(sessionId.value, message)
                coreCrypto.proteusSessionSave(sessionId.value)
                decryptedMessage
            }
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

    override suspend fun deleteSession(sessionId: CryptoSessionId) {
        wrapException {
            coreCrypto.proteusSessionDelete(sessionId.value)
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun <T> wrapException(b: suspend () -> T): T {
        try {
            return b()
        } catch (e: CoreCryptoException) {
            if (this::coreCrypto.isInitialized) {
                throw ProteusException(e.message, ProteusException.fromProteusCode(coreCrypto.proteusLastErrorCode().toInt()), e)
            } else {
                throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, e)
            }
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, e)
        }
    }

    private companion object {
        fun toPreKey(id: Int, data: ByteArray): PreKeyCrypto =
            PreKeyCrypto(id, data.encodeBase64())

        val CRYPTO_BOX_FILES = listOf("identities", "prekeys", "sessions", "version")
        const val KEYSTORE_NAME = "keystore"
    }
}
