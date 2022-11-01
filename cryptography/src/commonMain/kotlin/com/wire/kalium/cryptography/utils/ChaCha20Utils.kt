package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.ionspin.kotlin.crypto.secretstream.SecretStream
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_ABYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_HEADERBYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_FINAL
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_MESSAGE
import com.wire.kalium.cryptography.backup.Backup
import com.wire.kalium.cryptography.backup.Backup.Companion.BACKUP_FILE_HEADER_LENGTH
import com.wire.kalium.cryptography.kaliumLogger
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import kotlin.random.Random
import kotlin.random.nextUBytes

@OptIn(ExperimentalUnsignedTypes::class)
internal class ChaCha20Utils {

    @Suppress("TooGenericExceptionCaught")
    suspend fun encryptBackupFile(
        backupDataSource: Source,
        outputSink: Sink,
        backup: Backup
    ): Long {

        initializeLibsodiumIfNeeded()
        var encryptedDataSize = 0L
        val additionalInformation: UByteArray = ubyteArrayOf()

        try {
            val chaCha20Key = backup.generateChaCha20Key()
            val hashedUserId = backup.hashUserId()
            val backupHeader = backup.provideHeaderBuffer(hashedUserId).also {
                if (it.isEmpty()) throw IllegalStateException("Backup header is empty")
            }

            // We append all the metadata unencrypted to the beginning of the backup file data
            val outputBuffer = outputSink.buffer()
            outputBuffer.write(backupHeader)
            outputBuffer.flush()

            val stateAndHeader = SecretStream.xChaCha20Poly1305InitPush(chaCha20Key)
            val state = stateAndHeader.state
            val chachaHeader = stateAndHeader.header.toByteArray()

            // We write the ChaCha20 generated header prior to the encrypted backup file data
            outputBuffer.write(chachaHeader)
            outputBuffer.flush()

            val inputContentBuffer = Buffer()
            var byteCount: Long
            while (backupDataSource.read(inputContentBuffer, ENCRYPT_BUFFER_SIZE).also { byteCount = it } != -1L) {
                // We need to inform the end of the encryption stream with the TAG_FINAL
                val appendingTag = if (byteCount < ENCRYPT_BUFFER_SIZE) {
                    crypto_secretstream_xchacha20poly1305_TAG_FINAL
                } else
                    crypto_secretstream_xchacha20poly1305_TAG_MESSAGE

                val dataToEncrypt = inputContentBuffer.readByteArray(byteCount)
                val uByteDataToEncrypt = dataToEncrypt.toUByteArray()

                val encryptedData = SecretStream.xChaCha20Poly1305Push(
                    state,
                    uByteDataToEncrypt,
                    additionalInformation,
                    appendingTag.toUByte()
                )
                val tempWriteBuffer = Buffer()
                tempWriteBuffer.write(encryptedData.toByteArray())
                outputSink.write(tempWriteBuffer, tempWriteBuffer.size)
                encryptedDataSize += encryptedData.size
            }
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while encrypting the backup data with ChaCha20:\n $e}")
        } finally {
            backupDataSource.close()
            outputSink.close()
        }
        return encryptedDataSize
    }

    @Suppress("TooGenericExceptionCaught")
    @Throws(Exception::class)
    suspend fun decryptFile(
        encryptedDataSource: Source,
        decryptedDataSink: Sink,
        passphrase: Backup.Passphrase
    ): Long {

        initializeLibsodiumIfNeeded()
        var decryptedDataSize = 0L

        try {
            val headerBuffer = Buffer()
            encryptedDataSource.read(headerBuffer, BACKUP_FILE_HEADER_LENGTH)
            val fileHeaderBuffer = Backup.BackupHeaderData(headerBuffer.readByteArray())
            val salt = fileHeaderBuffer.extractSalt().toUByteArray()
            val backup = Backup(salt, passphrase)
            val additionalInformation: UByteArray = ubyteArrayOf()

            // Sanity checks
            val expectedHashedUserId = backup.hashUserId()
            val storedHashedUserId = fileHeaderBuffer.extractHashedUserId()
            check(expectedHashedUserId.contentEquals(storedHashedUserId.toUByteArray())) {
                "The hashed user id in the backup file header does not match the expected one"
            }

            val key = backup.generateChaCha20Key().toUByteArray()

            // ChaCha20 header is needed to validate the encrypted data hasn't been tampered with different authentication
            val chaChaHeaderBuffer = Buffer()
            encryptedDataSource.read(chaChaHeaderBuffer, crypto_secretstream_xchacha20poly1305_HEADERBYTES.toLong())
            val chaChaHeader = chaChaHeaderBuffer.readByteArray().toUByteArray()
            val secretStreamState = SecretStream.xChaCha20Poly1305InitPull(key, chaChaHeader)

            val contentBuffer = Buffer()
            var byteCount: Long
            val decryptionBufferSize = ENCRYPT_BUFFER_SIZE + crypto_secretstream_xchacha20poly1305_ABYTES
            while (encryptedDataSource.read(contentBuffer, decryptionBufferSize).also { byteCount = it } != -1L) {
                val encryptedData = contentBuffer.readByteArray(byteCount).toUByteArray()
                val (decryptedData, tag) = SecretStream.xChaCha20Poly1305Pull(
                    secretStreamState.state,
                    encryptedData,
                    additionalInformation
                ).let { it.decryptedData.toByteArray() to it.tag.toInt() }

                val tempReadBuffer = Buffer()
                tempReadBuffer.write(decryptedData)
                decryptedDataSink.write(tempReadBuffer, tempReadBuffer.size)
                decryptedDataSize += decryptedData.size

                // Stop reading the file if we reach the end of the stream
                if (tag == crypto_secretstream_xchacha20poly1305_TAG_FINAL) {
                    break
                }
            }
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while decrypting the backup data with ChaCha20:\n $e}")
        } finally {
            encryptedDataSource.close()
            decryptedDataSink.close()
        }
        return decryptedDataSize
    }

    private companion object {
        const val ENCRYPT_BUFFER_SIZE = 4096L
    }
}

internal suspend fun initializeLibsodiumIfNeeded() {
    if (!LibsodiumInitializer.isInitialized()) {
        LibsodiumInitializer.initialize()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun generateSalt() = Random(0).nextUBytes(crypto_pwhash_SALTBYTES)
