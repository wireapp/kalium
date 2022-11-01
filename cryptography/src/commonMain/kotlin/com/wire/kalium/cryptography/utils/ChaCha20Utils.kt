package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.secretstream.SecretStream
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_HEADERBYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_FINAL
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_MESSAGE
import com.ionspin.kotlin.crypto.util.encodeToUByteArray
import com.ionspin.kotlin.crypto.util.toHexString
import com.wire.kalium.cryptography.backup.Backup
import com.wire.kalium.cryptography.kaliumLogger
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer

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
        val additionalInformation: UByteArray = backup.backupVersion.encodeToUByteArray()

        try {
            val chaCha20Key = backup.provideChaCha20Key()
            val hashedUserId = backup.provideHashedUserId().toByteArray()
            val backupHeader = backup.provideHeaderBuffer(hashedUserId).also {
                if (it.isEmpty()) throw IllegalStateException("Backup header is empty")
            }

            // We append all the metadata unencrypted to the beginning of the backup file data
            val outputBuffer = outputSink.buffer()
            outputBuffer.write(backupHeader)
            outputBuffer.flush()

            val stateAndHeader = SecretStream.xChaCha20Poly1305InitPush(chaCha20Key)
            val state = stateAndHeader.state

            println("header: ${stateAndHeader.header.toHexString()}")

            outputBuffer.write(stateAndHeader.header.toByteArray())
            outputBuffer.flush()

            val contentBuffer = Buffer()
            var byteCount: Long
            while (backupDataSource.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
                val isExhausted = backupDataSource.buffer().exhausted()
                kaliumLogger.d("BackupDataSource exhausted: $isExhausted")

                // We need to inform the end of the encryption stream with the TAG_FINAL
                val appendingTag = if (byteCount == BUFFER_SIZE) {
                    crypto_secretstream_xchacha20poly1305_TAG_MESSAGE
                } else crypto_secretstream_xchacha20poly1305_TAG_FINAL

                val dataToEncrypt = contentBuffer.readByteArray(byteCount)
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
            encryptedDataSource.read(headerBuffer, FILE_HEADER_SIZE)
            val fileHeaderBuffer = headerBuffer.readByteArray()
            val salt = fileHeaderBuffer.copyOfRange(7, 23).toUByteArray()
            val backup = Backup(salt, passphrase)
            val additionalInformation: UByteArray = backup.backupVersion.encodeToUByteArray()
            val expectedHashedUserId = backup.provideHashedUserId().toByteArray()
            val storedHashedUserId = fileHeaderBuffer.copyOfRange(23, 55)
            check(expectedHashedUserId.contentEquals(storedHashedUserId)) {
                "The hashed user id in the backup file header does not match the expected one"
            }

//             println("decoded salt: ${salt.toByteArray().decodeToString()}")

            val key = backup.provideChaCha20Key()

            val chaChaHeaderBuffer = Buffer()
            encryptedDataSource.read(chaChaHeaderBuffer, crypto_secretstream_xchacha20poly1305_HEADERBYTES.toLong())
            val chaChaHeader = chaChaHeaderBuffer.readByteArray().toUByteArray()

            println("header: ${chaChaHeader.toHexString()}")

            val secretStreamState = SecretStream.xChaCha20Poly1305InitPull(key, chaChaHeader)

            val contentBuffer = Buffer()
            var byteCount: Long
            while (encryptedDataSource.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
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
    private suspend fun initializeLibsodiumIfNeeded() {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 4096L
        const val FILE_HEADER_SIZE = 63L
    }

}
