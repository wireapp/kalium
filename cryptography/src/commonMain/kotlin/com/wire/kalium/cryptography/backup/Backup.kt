package com.wire.kalium.cryptography.backup

import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.utils.initializeLibsodiumIfNeeded
import okio.Buffer
import okio.BufferedSource
import okio.IOException
import okio.Source

@OptIn(ExperimentalUnsignedTypes::class)
class Backup(val salt: UByteArray, val userId: CryptoUserID, val passphrase: Passphrase) {

    suspend fun provideHeaderBuffer(): ByteArray {
        val hashedUserId = hashUserId(userId, salt)
        return Header(format, version, salt, hashedUserId, OPSLIMIT_INTERACTIVE_VALUE, MEMLIMIT_INTERACTIVE_VALUE).toByteArray()
    }

    data class Passphrase(val password: String)

    data class Header(
        val format: String,
        val version: String,
        val salt: UByteArray,
        val hashedUserId: UByteArray,
        val opslimit: Int,
        val memlimit: Int
    ) {

        fun toByteArray(): ByteArray {
            val buffer = Buffer()
            buffer.write(format.encodeToByteArray())
            buffer.write(extraGap)
            buffer.write(version.encodeToByteArray())
            buffer.write(salt.toByteArray())
            buffer.write(hashedUserId.toByteArray())
            buffer.writeInt(opslimit)
            buffer.writeInt(memlimit)

            return buffer.readByteArray()
        }

        companion object {

            @Throws(IOException::class)
            fun Source.readBackupHeader(): Header {
                try {
                    val readBuffer = Buffer()
                    // We read the backup header and execute some sanity checks
                    val format = this.read(readBuffer, BACKUP_HEADER_FORMAT_LENGTH).let { size ->
                        readBuffer.readByteArray(size).decodeToString().also {
                            if (it != format) throw IllegalStateException("Backup format is not valid")
                            readBuffer.clear()
                        }
                    }

                    // We skip the extra gap
                    read(readBuffer, BACKUP_HEADER_EXTRA_GAP_LENGTH).also { readBuffer.clear() }

                    val version = read(readBuffer, BACKUP_HEADER_VERSION_LENGTH).let { size ->
                        readBuffer.readByteArray(size).decodeToString().also {
                            if (it != version) throw IllegalStateException("Backup version is not valid")
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
                        readBuffer.readInt().also {
                            // TODO: Do some checkings if opslimit value changes between versions?
                            readBuffer.clear()
                        }
                    }

                    val memlimit = this.read(readBuffer, UNSIGNED_INT_LENGTH).let {
                        readBuffer.readInt().also {
                            // TODO: Do some checkings if opslimit value changes between versions?
                            readBuffer.clear()
                        }
                    }

                    return Header(
                        format = format,
                        version = version,
                        salt = salt,
                        hashedUserId = hashedUserId,
                        opslimit = opslimit,
                        memlimit = memlimit
                    )
                } catch (e: IOException) {
                    throw IllegalStateException("Backup provided can't be read", e)
                }
            }

        }
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

        private val extraGap = byteArrayOf(0x00)

        // Wire Backup Generic format identifier
        private const val format = "WBUX"

        // Current Wire Backup version
        const val version = "03"

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
                userId.value,  // TODO: Should we hash only the user id value or the whole object with domain?
                salt,
                OPSLIMIT_INTERACTIVE_VALUE.toULong(),
                MEMLIMIT_INTERACTIVE_VALUE,
                crypto_pwhash_ALG_DEFAULT
            )
        }
    }
}
