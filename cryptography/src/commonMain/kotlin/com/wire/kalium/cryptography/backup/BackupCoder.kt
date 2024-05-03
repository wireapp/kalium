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

package com.wire.kalium.cryptography.backup

import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_FORMAT
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_USER_ID
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_VERSION
import com.wire.kalium.cryptography.kaliumLogger
import okio.Buffer
import okio.IOException
import okio.Source
import kotlin.random.Random
import kotlin.random.nextUBytes

@OptIn(ExperimentalUnsignedTypes::class)
class BackupCoder(val userId: CryptoUserID, val passphrase: Passphrase) {

    fun encodeHeader(): BackupHeader {
        val salt = Random.nextUBytes(crypto_pwhash_SALTBYTES)
        val hashedUserId = hashUserId(userId, salt, OPSLIMIT_INTERACTIVE_VALUE, MEMLIMIT_INTERACTIVE_VALUE)
        return BackupHeader(format, version, salt, hashedUserId, OPSLIMIT_INTERACTIVE_VALUE, MEMLIMIT_INTERACTIVE_VALUE)
    }

    fun decodeHeader(encryptedDataSource: Source): Pair<HeaderDecodingErrors?, BackupHeader> {
        val decodedHeader = encryptedDataSource.readBackupHeader()

        // Sanity checks
        val expectedHashedUserId = hashUserId(userId, decodedHeader.salt, decodedHeader.opslimit, decodedHeader.memlimit)
        val storedHashedUserId = decodedHeader.hashedUserId
        val decodingError = handleHeaderDecodingErrors(decodedHeader, expectedHashedUserId, storedHashedUserId)
        return decodingError to decodedHeader
    }

    private fun handleHeaderDecodingErrors(
        decodedHeader: BackupHeader,
        expectedHashedUserId: UByteArray,
        storedHashedUserId: UByteArray
    ): HeaderDecodingErrors? =
        when {
            !expectedHashedUserId.contentEquals(storedHashedUserId.toUByteArray()) -> {
                kaliumLogger.e("The hashed user id in the backup file header does not match the expected one")
                INVALID_USER_ID
            }
            decodedHeader.format != format -> {
                kaliumLogger.e("The backup format found in the backup file header is not a valid one")
                INVALID_FORMAT
            }
            decodedHeader.version.toInt() < version.toInt() -> {
                kaliumLogger.e("The backup version found in the backup file header is not a valid one")
                INVALID_VERSION
            }
            else -> null
        }

    @Suppress("ComplexMethod")
    @Throws(IOException::class)
    private fun Source.readBackupHeader(): BackupHeader {
        val readBuffer = Buffer()

        // We read the backup header and execute some sanity checks
        val format = this.read(readBuffer, BACKUP_HEADER_FORMAT_LENGTH).let { size ->
            readBuffer.readByteArray(size).decodeToString().also {
                readBuffer.clear()
            }
        }

        // We skip the extra gap
        read(readBuffer, BACKUP_HEADER_EXTRA_GAP_LENGTH).also { readBuffer.clear() }

        val version = read(readBuffer, BACKUP_HEADER_VERSION_LENGTH).let { size ->
            readBuffer.readByteArray(size).decodeToString().also {
                readBuffer.clear()
            }
        }

        val salt = read(readBuffer, crypto_pwhash_SALTBYTES.toLong()).let { size ->
            readBuffer.readByteArray(size).toUByteArray().also { readBuffer.clear() }
        }

        val hashedUserId = read(readBuffer, PWD_HASH_OUTPUT_BYTES.toLong()).let { size ->
            readBuffer.readByteArray(size).toUByteArray().also { readBuffer.clear() }
        }

        val opslimit = this.read(readBuffer, UNSIGNED_INT_LENGTH).let {
            readBuffer.readInt().also { readBuffer.clear() }
        }

        val memlimit = this.read(readBuffer, UNSIGNED_INT_LENGTH).let {
            readBuffer.readInt().also { readBuffer.clear() }
        }

        return BackupHeader(
            format = format,
            version = version,
            salt = salt,
            hashedUserId = hashedUserId,
            opslimit = opslimit,
            memlimit = memlimit
        )
    }

    // ChaCha20 SecretKey used to encrypt derived from the passphrase (salt + provided password)
    internal fun generateChaCha20Key(header: BackupHeader): UByteArray {
        return PasswordHash.pwhash(
            PWD_HASH_OUTPUT_BYTES,
            passphrase.password,
            header.salt,
            header.opslimit.toULong(),
            header.memlimit,
            crypto_pwhash_ALG_DEFAULT
        )
    }

    private fun hashUserId(userId: CryptoUserID, salt: UByteArray, opslimit: Int, memlimit: Int): UByteArray {
        return PasswordHash.pwhash(
            PWD_HASH_OUTPUT_BYTES,
            userId.toString(),
            salt,
            opslimit.toULong(),
            memlimit,
            crypto_pwhash_ALG_DEFAULT
        )
    }

    companion object {
        // Defined by given specs on: https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/59867179/Exporting+history+v2
        private const val MEMLIMIT_INTERACTIVE_VALUE = 33554432
        private const val OPSLIMIT_INTERACTIVE_VALUE = 4
        private const val PWD_HASH_OUTPUT_BYTES = 32
        private const val UNSIGNED_INT_LENGTH = 4L
        private const val BACKUP_HEADER_EXTRA_GAP_LENGTH = 1L
        private const val BACKUP_HEADER_FORMAT_LENGTH = 4L
        private const val BACKUP_HEADER_VERSION_LENGTH = 2L

        // Wire Backup Generic format identifier
        private const val format = "WBUX"

        // Current Wire Backup version
        const val version = "03"
    }
}
