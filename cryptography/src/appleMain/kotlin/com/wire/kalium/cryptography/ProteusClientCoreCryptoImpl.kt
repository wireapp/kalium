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
import io.ktor.util.encodeBase64
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import kotlin.coroutines.cancellation.CancellationException

@Suppress("TooManyFunctions")
class ProteusClientCoreCryptoImpl private constructor(private val coreCrypto: CoreCrypto) : ProteusClient {
    @Suppress("EmptyFunctionBlock")
    override suspend fun close() {
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

    override suspend fun <R> transaction(name: String, block: suspend (context: ProteusCoreCryptoContext) -> R): R {
        TODO("Not yet implemented")
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private inline fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            // TODO underlying proteus error is not exposed atm
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, null, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, null, null)
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

        @Suppress("TooGenericExceptionCaught", "ThrowsCount")
        operator fun invoke(coreCrypto: CoreCrypto, rootDir: String): ProteusClientCoreCryptoImpl {
            try {
                migrateFromCryptoBoxIfNecessary(coreCrypto, rootDir)
                coreCrypto.proteusInit()
                return ProteusClientCoreCryptoImpl(coreCrypto)
            } catch (e: CryptoException) {
                throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, null, e.cause)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, null, e.cause)
            }
        }
    }
}
