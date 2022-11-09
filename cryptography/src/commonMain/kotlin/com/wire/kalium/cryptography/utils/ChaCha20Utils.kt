package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.ionspin.kotlin.crypto.secretstream.SecretStream
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_ABYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_HEADERBYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_FINAL
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_MESSAGE
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.Backup
import com.wire.kalium.cryptography.backup.Backup.Header.Companion.readBackupHeader
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
        val additionalInformation: UByteArray = Backup.version.encodeToByteArray().toUByteArray()
        val outputBufferedSink = outputSink.buffer()

        try {
            val chaCha20Key = Backup.generateChaCha20Key(backup.passphrase, backup.salt)
            val backupHeader = backup.provideHeaderBuffer().also {
                if (it.isEmpty()) throw IllegalStateException("Backup header is empty")
            }

            // We append all the metadata unencrypted to the beginning of the backup file data
            outputBufferedSink.write(backupHeader)

            val stateAndHeader = SecretStream.xChaCha20Poly1305InitPush(chaCha20Key)
            val state = stateAndHeader.state
            val chachaHeader = stateAndHeader.header.toByteArray()

            // We write the ChaCha20 generated header prior to the encrypted backup file data
            outputBufferedSink.write(chachaHeader)

            val inputContentBuffer = Buffer()
            var byteCount: Long
            while (backupDataSource.read(inputContentBuffer, ENCRYPT_BUFFER_SIZE).also { byteCount = it } != -1L) {
                // We need to inform the end of the encryption stream with the TAG_FINAL
                val appendingTag = if (byteCount < ENCRYPT_BUFFER_SIZE) { // TODO: Find a better way to detect the end of the stream
                    crypto_secretstream_xchacha20poly1305_TAG_FINAL
                } else
                    crypto_secretstream_xchacha20poly1305_TAG_MESSAGE

                val dataToEncrypt = inputContentBuffer.readByteArray(byteCount).toUByteArray()
                val encryptedData = SecretStream.xChaCha20Poly1305Push(
                    state,
                    dataToEncrypt,
                    additionalInformation,
                    appendingTag.toUByte()
                )
                outputBufferedSink.write(encryptedData.toByteArray())
                encryptedDataSize += encryptedData.size
            }
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while encrypting the backup data with ChaCha20:\n $e}")
        } finally {
            backupDataSource.close()
            outputBufferedSink.close()
        }
        return encryptedDataSize
    }

    @Suppress("TooGenericExceptionCaught")
    @Throws(Exception::class)
    suspend fun decryptBackupFile(
        encryptedDataSource: Source,
        decryptedDataSink: Sink,
        passphrase: Backup.Passphrase,
        userId: CryptoUserID
    ): Long {

        initializeLibsodiumIfNeeded()
        var decryptedDataSize = 0L
        val decryptionBufferedSink = decryptedDataSink.buffer()

        try {
            val additionalInformation: UByteArray = Backup.version.encodeToByteArray().toUByteArray()
            val fileHeaderBuffer = encryptedDataSource.readBackupHeader()
            val salt = fileHeaderBuffer.salt

            // Sanity checks
            val expectedHashedUserId = Backup.hashUserId(userId, salt)
            val storedHashedUserId = fileHeaderBuffer.hashedUserId
            check(expectedHashedUserId.contentEquals(storedHashedUserId.toUByteArray())) {
                "The hashed user id in the backup file header does not match the expected one"
            }

            val key = Backup.generateChaCha20Key(passphrase, salt).toUByteArray()

            // ChaCha20 header is needed to validate the encrypted data hasn't been tampered with different authentication
            val chaChaHeaderBuffer = Buffer()
            encryptedDataSource.read(chaChaHeaderBuffer, crypto_secretstream_xchacha20poly1305_HEADERBYTES.toLong())
            val chaChaHeader = chaChaHeaderBuffer.readByteArray()
            val secretStreamState = SecretStream.xChaCha20Poly1305InitPull(key, chaChaHeader.toUByteArray())

            // Decrypt the backup file data reading it in chunks
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

                decryptionBufferedSink.write(decryptedData)
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
            decryptionBufferedSink.close()
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
