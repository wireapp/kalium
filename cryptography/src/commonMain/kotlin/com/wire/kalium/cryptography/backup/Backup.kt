package com.wire.kalium.cryptography.backup

import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_FINAL
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.utils.PlainData

@OptIn(ExperimentalUnsignedTypes::class)
class Backup(val salt: PlainData, val passphrase: Passphrase) {

    // Wire Backup Generic format identifier
    val backupFormat = "WBUX"

    // Current Wire Backup version
    val backupVersion = "03"

    // ChaCha20 SecretKey used to encrypt derived from the passphrase (salt + provided password)
    fun provideChaCha20Key() = PasswordHash.pwhash(
        passphrase.password.length,
        passphrase.password,
        salt.data.toUByteArray(),
        OPSLIMIT_INTERACTIVE_VALUE,
        MEMLIMIT_INTERACTIVE_VALUE,
        crypto_secretstream_xchacha20poly1305_TAG_FINAL
    )

    fun provideHashedUserId() = PasswordHash.pwhash(
        passphrase.userId.value.length,
        passphrase.userId.value,
        salt.data.toUByteArray(),
        OPSLIMIT_INTERACTIVE_VALUE,
        MEMLIMIT_INTERACTIVE_VALUE,
        crypto_pwhash_ALG_DEFAULT
    ).toByteArray()

    fun provideHeaderBuffer(hashedUserId: ByteArray): ByteArray = backupFormat.encodeToByteArray() + extraGap +
            backupVersion.encodeToByteArray() + salt.data + hashedUserId + OPSLIMIT_INTERACTIVE_VALUE.toLong().toByte() +
            MEMLIMIT_INTERACTIVE_VALUE.toLong().toByte()

    data class Passphrase(
        val password: String,
        val userId: CryptoUserID
    )

    companion object {
        // Defined by given specs on: https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/59867179/Exporting+history+v2
        private const val MEMLIMIT_INTERACTIVE_VALUE = 33554432
        private const val OPSLIMIT_INTERACTIVE_VALUE = 4UL

        val extraGap = byteArrayOf(0x00)
    }
}
