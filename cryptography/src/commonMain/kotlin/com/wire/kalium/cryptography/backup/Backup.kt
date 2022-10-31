package com.wire.kalium.cryptography.backup

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.wire.kalium.cryptography.CryptoUserID

@OptIn(ExperimentalUnsignedTypes::class)
class Backup(val salt: UByteArray, val passphrase: Passphrase) {

    // Wire Backup Generic format identifier
    val backupFormat = "WBUX"

    // Current Wire Backup version
    val backupVersion = "03"

    // ChaCha20 SecretKey used to encrypt derived from the passphrase (salt + provided password)
    suspend fun provideChaCha20Key(): UByteArray {
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

    suspend fun provideHashedUserId(): UByteArray {
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

    fun provideHeaderBuffer(hashedUserId: ByteArray): ByteArray = backupFormat.encodeToByteArray() + extraGap +
            backupVersion.encodeToByteArray() + salt.toByteArray() + hashedUserId + OPSLIMIT_INTERACTIVE_VALUE.toUInt32ByteArray() +
            MEMLIMIT_INTERACTIVE_VALUE.toUInt32ByteArray()

    data class Passphrase(
        val password: String,
        val userId: CryptoUserID
    )

    private suspend fun initializeLibsodiumIfNeeded() {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
    }

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

    companion object {
        // Defined by given specs on: https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/59867179/Exporting+history+v2
        private const val MEMLIMIT_INTERACTIVE_VALUE = 33554432
        private const val OPSLIMIT_INTERACTIVE_VALUE = 4UL
        private const val PWD_HASH_OUTPUT_BYTES = 32

        val extraGap = byteArrayOf(0x00)
    }
}
