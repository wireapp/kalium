package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.utils.io.core.use
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import okio.cipherSink
import okio.cipherSource
import okio.source
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.ChaCha20ParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class ChaCha20 {
    @Throws(Exception::class)
    @Suppress("TooGenericExceptionCaught")
    fun encryptBackupFile(
        backupDataSource: Source,
        outputSink: Sink,
        key: ChaCha20Key,
        nonce: ByteArray
    ): Long {
        var encryptedDataSize = 0L
        try {
            val counter = 1
            val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)
            val param = ChaCha20ParameterSpec(nonce, counter)

            val secretChaCha20Key = SecretKeySpec(key.data, 0, key.data.size, KEY_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretChaCha20Key, param)

            val outputBuffer = outputSink.buffer()
            outputBuffer.cipherSink(cipher).buffer().use { cipheredSink ->
                val contentBuffer = Buffer()
                var byteCount: Long
                while (backupDataSource.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
                    encryptedDataSize += byteCount
                    cipheredSink.write(contentBuffer, byteCount)
                    cipheredSink.flush()
                }
                encryptedDataSize += nonce.size
                outputBuffer.write(nonce)
            }
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while encrypting the backup data with ChaCha20:\n $e}")
        } finally {
            backupDataSource.close()
            outputSink.close()
        }
        return encryptedDataSize
    }

    @Throws(Exception::class)
    fun decryptBackupFile(
        encryptedDataSource: Source,
        outputSink: Sink,
        key: SecretKey,
        salt: ByteArray
    ): Long {
        var decryptedDataSize = 0L
        try {
            val counter = 1
            val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)
            val param = ChaCha20ParameterSpec(salt, counter)
            cipher.init(Cipher.DECRYPT_MODE, key, param)

            // we append the salt right after the file data
            val outputBuffer = outputSink.buffer()
            outputBuffer.write(cipher.iv)
            outputBuffer.flush()

            encryptedDataSource.cipherSource(cipher).buffer().use { bufferedSource ->
                val source = bufferedSource.inputStream().source()
                val contentBuffer = Buffer()
                var byteCount: Long
                while (source.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
                    decryptedDataSize += byteCount
                    outputSink.write(contentBuffer, byteCount)
                    outputSink.flush()
                }
                source.close()
            }
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while decrypting the backup data with ChaCha20:\n $e}")
        } finally {
            encryptedDataSource.close()
            outputSink.close()
        }
        return decryptedDataSize
    }

    companion object {
        private const val KEY_ALGORITHM = "ChaCha20"
        private const val KEY_ALGORITHM_CONFIGURATION = "ChaCha20-Poly1305"
        private const val BUFFER_SIZE = 1024 * 4L
    }
}
