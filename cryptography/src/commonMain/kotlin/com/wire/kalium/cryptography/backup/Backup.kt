package com.wire.kalium.cryptography.backup

import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.utils.initializeLibsodiumIfNeeded
import okio.Buffer

@OptIn(ExperimentalUnsignedTypes::class)
class Backup(val salt: UByteArray, val userId: CryptoUserID, val passphrase: Passphrase) {

    suspend fun provideHeaderBuffer(): ByteArray {
        val hashedUserId = hashUserId(userId, salt)
        return Header(salt, hashedUserId).toByteArray()
    }

    data class Passphrase(val password: String)

    data class Header(val salt: UByteArray, val hashedUserId: UByteArray) {
        constructor(data: Buffer): this(data.extractSalt(), data.extractHashedUserId())

        fun toByteArray(): ByteArray {
            val buffer = Buffer()
            buffer.write(format.encodeToByteArray())
            buffer.write(extraGap)
            buffer.write(version.encodeToByteArray())
            buffer.write(salt.toByteArray())
            buffer.write(hashedUserId.toByteArray())
            buffer.writeInt(OPSLIMIT_INTERACTIVE_VALUE)
            buffer.writeInt(MEMLIMIT_INTERACTIVE_VALUE)

            return buffer.readByteArray()
        }

        private companion object {

            private fun Buffer.extractSalt(): UByteArray {
                readByteArray(BACKUP_HEADER_FORMAT_LENGTH)
                readByte()
                readByteArray(BACKUP_HEADER_VERSION_LENGTH)
                return readByteArray(BACKUP_HEADER_SALT_LENGTH).toUByteArray()
            }

            private fun Buffer.extractHashedUserId(): UByteArray = readByteArray(PWD_HASH_OUTPUT_BYTES.toLong()).toUByteArray()
        }
    }

    companion object {
        // Defined by given specs on: https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/59867179/Exporting+history+v2
        private const val MEMLIMIT_INTERACTIVE_VALUE = 33554432
        private const val OPSLIMIT_INTERACTIVE_VALUE = 4
        private const val PWD_HASH_OUTPUT_BYTES = 32
        private const val BACKUP_HEADER_EXTRA_GAP_LENGTH = 1L
        private const val BACKUP_HEADER_FORMAT_LENGTH = 4L
        private const val BACKUP_HEADER_VERSION_LENGTH = 2L
        private const val BACKUP_HEADER_SALT_LENGTH = 16L
        private const val BACKUP_HEADER_OPSLIMIT_INTERACTIVE_LENGTH = 4L
        private const val BACKUP_HEADER_MEMLIMIT_INTERACTIVE_LENGTH = 4L

        private val extraGap = byteArrayOf(0x00)

        // Wire Backup Generic format identifier
        private const val format = "WBUX"

        // Current Wire Backup version
        const val version = "03"

        val BACKUP_FILE_HEADER_LENGTH: Long
            get() = BACKUP_HEADER_FORMAT_LENGTH + BACKUP_HEADER_EXTRA_GAP_LENGTH + BACKUP_HEADER_VERSION_LENGTH +
                    crypto_pwhash_SALTBYTES + PWD_HASH_OUTPUT_BYTES.toLong() + BACKUP_HEADER_OPSLIMIT_INTERACTIVE_LENGTH +
                    BACKUP_HEADER_MEMLIMIT_INTERACTIVE_LENGTH

        // ChaCha20 SecretKey used to encrypt derived from the passphrase (salt + provided password)
        internal suspend fun generateChaCha20Key(passphrase: Passphrase, salt: UByteArray): UByteArray {
            initializeLibsodiumIfNeeded()
            return PasswordHash.pwhash(
                PWD_HASH_OUTPUT_BYTES,
                passphrase.password,
                salt,
                OPSLIMIT_INTERACTIVE_VALUE.toULong(),
                MEMLIMIT_INTERACTIVE_VALUE,
                crypto_pwhash_ALG_DEFAULT
            )
        }

        internal suspend fun hashUserId(userId: CryptoUserID, salt: UByteArray): UByteArray {
            initializeLibsodiumIfNeeded()
            return PasswordHash.pwhash(
                PWD_HASH_OUTPUT_BYTES,
                userId.value,
                salt,
                OPSLIMIT_INTERACTIVE_VALUE.toULong(),
                MEMLIMIT_INTERACTIVE_VALUE,
                crypto_pwhash_ALG_DEFAULT
            )
        }
    }
}
