package com.wire.kalium.cryptography.backup

import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.utils.initializeLibsodiumIfNeeded

@OptIn(ExperimentalUnsignedTypes::class)
class Backup(private val salt: UByteArray, private val passphrase: Passphrase) {

    // ChaCha20 SecretKey used to encrypt derived from the passphrase (salt + provided password)
    suspend fun generateChaCha20Key(): UByteArray {
        initializeLibsodiumIfNeeded()
        return PasswordHash.pwhash(
            PWD_HASH_OUTPUT_BYTES,
            passphrase.password,
            salt,
            OPSLIMIT_INTERACTIVE_VALUE,
            MEMLIMIT_INTERACTIVE_VALUE,
            crypto_pwhash_ALG_DEFAULT
        )
    }

    suspend fun hashUserId(): UByteArray {
        initializeLibsodiumIfNeeded()
        return PasswordHash.pwhash(
            PWD_HASH_OUTPUT_BYTES,
            passphrase.userId.value,
            salt,
            OPSLIMIT_INTERACTIVE_VALUE,
            MEMLIMIT_INTERACTIVE_VALUE,
            crypto_pwhash_ALG_DEFAULT
        )
    }

    fun provideHeaderBuffer(hashedUserId: UByteArray): ByteArray = format.encodeToByteArray() + extraGap +
            VERSION.encodeToByteArray() + salt.toByteArray() + hashedUserId.toByteArray() + OPSLIMIT_INTERACTIVE_VALUE.toUInt32ByteArray() +
            MEMLIMIT_INTERACTIVE_VALUE.toUInt32ByteArray()

    data class Passphrase(
        val password: String,
        val userId: CryptoUserID
    )

    class BackupHeaderData(val data: ByteArray) {

        fun extractSalt(): ByteArray = data.copyOfRange(HEADER_SALT_START_INDEX, HEADER_SALT_END_INDEX)
        fun extractHashedUserId(): ByteArray = data.copyOfRange(HEADER_HASHED_ID_START_INDEX, HEADER_HASHED_ID_END_INDEX)
    }

    companion object {
        // Defined by given specs on: https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/59867179/Exporting+history+v2
        private const val MEMLIMIT_INTERACTIVE_VALUE = 33554432
        private const val OPSLIMIT_INTERACTIVE_VALUE = 4UL
        private const val PWD_HASH_OUTPUT_BYTES = 32
        private const val HEADER_SALT_START_INDEX = 7
        private const val HEADER_SALT_END_INDEX = 23
        private const val HEADER_HASHED_ID_START_INDEX = 23
        private const val HEADER_HASHED_ID_END_INDEX = 55

        private val extraGap = byteArrayOf(0x00)

        // Wire Backup Generic format identifier
        private const val format = "WBUX"

        // Current Wire Backup version
        const val VERSION = "03"

        val BACKUP_FILE_HEADER_LENGTH: Long
            get() = format.encodeToByteArray().size + extraGap.size + VERSION.encodeToByteArray().size +
                    crypto_pwhash_SALTBYTES + PWD_HASH_OUTPUT_BYTES.toLong() + OPSLIMIT_INTERACTIVE_VALUE.toUInt32ByteArray().size +
                    MEMLIMIT_INTERACTIVE_VALUE.toUInt32ByteArray().size

        fun ULong.toUInt32ByteArray(): ByteArray {
            val value = this.toLong()
            val bytes = ByteArray(4)
            bytes[3] = (value and 0xFFFF).toByte()
            bytes[2] = ((value ushr 8) and 0xFFFF).toByte()
            bytes[1] = ((value ushr 16) and 0xFFFF).toByte()
            bytes[0] = ((value ushr 24) and 0xFFFF).toByte()
            return bytes
        }

        fun Int.toUInt32ByteArray(): ByteArray {
            val value = this
            val bytes = ByteArray(4)
            for (i in 0..3) bytes[i] = (value shr (i * 8)).toByte()
            return bytes
        }
    }
}
