package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.secretstream.SecretStream
import com.ionspin.kotlin.crypto.secretstream.SecretStreamCorruptedOrTamperedDataException
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_ABYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_HEADERBYTES
import com.ionspin.kotlin.crypto.secretstream.crypto_secretstream_xchacha20poly1305_TAG_FINAL
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.BackupHeader
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.kaliumLogger
import com.wire.kalium.cryptography.utils.LibsodiumInitializer.initializeLibsodiumIfNeeded
import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Source
import okio.buffer

@OptIn(ExperimentalUnsignedTypes::class)
object ChaCha20Decryptor {

    private const val ENCRYPT_BUFFER_SIZE = 4096L

    suspend fun decryptBackupFile(
        encryptedDataSource: Source,
        decryptedDataSink: Sink,
        passphrase: Passphrase,
        userId: CryptoUserID
    ): Pair<BackupHeader.HeaderDecodingErrors?, Long> {

        initializeLibsodiumIfNeeded()
        var decryptedDataSize = 0L
        val decryptionBufferedSink = decryptedDataSink.buffer()

        try {
            val additionalInformation: UByteArray = BackupCoder.version.encodeToByteArray().toUByteArray()
            val backupCoder = BackupCoder(userId, passphrase)
            val (decodingError, header) = backupCoder.decodeHeader(encryptedDataSource)

            // If there was an error decoding the header, we return it
            decodingError?.let { return it to 0L }

            // We need to read the ChaCha20 generated header prior to the encrypted backup file data to run some sanity checks
            val chaChaHeaderKey = backupCoder.generateChaCha20Key(header)

            // ChaCha20 header is needed to validate the encrypted data hasn't been tampered with different authentication
            val chaChaHeaderBuffer = Buffer()
            encryptedDataSource.read(chaChaHeaderBuffer, crypto_secretstream_xchacha20poly1305_HEADERBYTES.toLong())
            val chaChaHeader = chaChaHeaderBuffer.readByteArray()
            val secretStreamState = SecretStream.xChaCha20Poly1305InitPull(chaChaHeaderKey, chaChaHeader.toUByteArray())

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

                val isEndOfTheStream = tag == crypto_secretstream_xchacha20poly1305_TAG_FINAL
                if (isEndOfTheStream) {
                    break
                }
            }
        } catch (e: IOException) {
            kaliumLogger.e("There was an error decrypting backup data:\n $e}")
        } catch (e: IllegalStateException) {
            kaliumLogger.e("There was an error decoding backup header data. Stored hashed userId differs from the provided one:\n $e}")
        } catch (e: SecretStreamCorruptedOrTamperedDataException) {
            kaliumLogger.e("Error while decrypting the backup data with ChaCha20. Probably the provided password is wrong:\n $e}")
        } finally {
            encryptedDataSource.close()
            decryptionBufferedSink.close()
        }
        return null to decryptedDataSize
    }
}
