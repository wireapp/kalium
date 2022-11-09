package com.wire.kalium.cryptography.backup

import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.wire.kalium.cryptography.CryptoUserID
import okio.Buffer
import okio.IOException
import okio.Source
import kotlin.random.Random
import kotlin.random.nextUBytes

@OptIn(ExperimentalUnsignedTypes::class)
class BackupCoder(val userId: CryptoUserID, val passphrase: Passphrase) {

    fun encodeHeader(): Header {
        val salt = Random.nextUBytes(crypto_pwhash_SALTBYTES)
        val hashedUserId = hashUserId(userId, salt, OPSLIMIT_INTERACTIVE_VALUE, MEMLIMIT_INTERACTIVE_VALUE)
        return Header(format, version, salt, hashedUserId, OPSLIMIT_INTERACTIVE_VALUE, MEMLIMIT_INTERACTIVE_VALUE)
    }

    fun decodeHeader(encryptedDataSource: Source): Header {
        val decodedHeader = encryptedDataSource.readBackupHeader()

        // Sanity checks
        val expectedHashedUserId = hashUserId(userId, decodedHeader.salt, decodedHeader.opslimit, decodedHeader.memlimit)
        val storedHashedUserId = decodedHeader.hashedUserId
        check(expectedHashedUserId.contentEquals(storedHashedUserId.toUByteArray())) {
            "The hashed user id in the backup file header does not match the expected one"
        }
        return decodedHeader
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
    }

    @Suppress("ComplexMethod")
    @Throws(IOException::class)
    private fun Source.readBackupHeader(): Header {
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
            readBuffer.readInt().also { readBuffer.clear() }
        }

        val memlimit = this.read(readBuffer, UNSIGNED_INT_LENGTH).let {
            readBuffer.readInt().also { readBuffer.clear() }
        }

        return Header(
            format = format,
            version = version,
            salt = salt,
            hashedUserId = hashedUserId,
            opslimit = opslimit,
            memlimit = memlimit
        )
    }

    // ChaCha20 SecretKey used to encrypt derived from the passphrase (salt + provided password)
    internal fun generateChaCha20Key(header: Header): UByteArray {
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

        private val extraGap = byteArrayOf(0x00)

        // Wire Backup Generic format identifier
        private const val format = "WBUX"

        // Current Wire Backup version
        const val version = "03"
    }
}
