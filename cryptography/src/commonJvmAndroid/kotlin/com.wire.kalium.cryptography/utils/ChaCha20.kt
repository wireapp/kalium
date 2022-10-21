package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.core.use
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import okio.cipherSink
import okio.cipherSource
import okio.source
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.ChaCha20ParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class ChaCha20 {
    @Throws(Exception::class)
    @Suppress("TooGenericExceptionCaught")
    fun encryptFile(
        backupDataSource: Source,
        outputSink: Sink,
        key: ChaCha20Key,
        salt: PlainData,
        hashedUserId: PlainData
    ): Long {
        var encryptedDataSize = 0L
        try {
            val magicNumber = "WBUA".toByteArray()
            val extraGap = 0x00.toString().toByteArray()
            val version = (0x00.and(0x03)).toString().toByteArray()

            val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)
            val param = IvParameterSpec(salt.data)

            val secretChaCha20Key = SecretKeySpec(key.data, KEY_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretChaCha20Key, param)

            // We append all the metadata to the beginning of the file data
            val outputBuffer = outputSink.buffer()
            outputBuffer.write(magicNumber)
            outputBuffer.write(extraGap)
            outputBuffer.write(version)
            outputBuffer.write(salt.data)
            outputBuffer.write(hashedUserId.data)
            outputBuffer.flush()

            outputSink.buffer().cipherSink(cipher).buffer().use { cipheredSink ->
                val contentBuffer = Buffer()
                var byteCount: Long
                while (backupDataSource.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
                    encryptedDataSize += byteCount
                    cipheredSink.write(contentBuffer, byteCount)
                    cipheredSink.flush()
                }
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
    fun decryptFile(
        encryptedDataSource: Source,
        outputSink: Sink,
        key: ChaCha20Key,
        salt: PlainData
    ): Long {
        var decryptedDataSize = 0L
        try {
            val counter = 1
            val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)
            val param = ChaCha20ParameterSpec(salt.data, counter)
            val secretChaCha20Key = SecretKeySpec(key.data, 0, key.data.size, KEY_ALGORITHM)

            cipher.init(Cipher.DECRYPT_MODE, secretChaCha20Key, param)

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

    internal fun generateRandomChaCha20Key(): ChaCha20Key {
        val keygen = KeyGenerator.getInstance(KEY_ALGORITHM)
        keygen.init(CHACHA20_KEY_SIZE, SecureRandom.getInstanceStrong())
        return ChaCha20Key(keygen.generateKey().encoded)
    }

    companion object {
        private const val KEY_ALGORITHM = "ChaCha20"
        private const val KEY_ALGORITHM_CONFIGURATION = "ChaCha20-Poly1305/None/NoPadding"
        private const val BUFFER_SIZE = 1024 * 4L
        private const val CHACHA20_KEY_SIZE = 256
    }
}
