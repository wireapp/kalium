package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.utils.io.core.use
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import okio.cipherSink
import okio.cipherSource
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class AESEncrypt {

    @Suppress("TooGenericExceptionCaught")
    internal fun encryptFile(assetDataSource: Source, key: AES256Key, outputSink: Sink): Long {
        var encryptedDataSize = 0L
        try {
            // Fetch AES256 Algorithm
            val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

            // Parse Secret Key from our custom AES256Key model object
            val symmetricAESKey = SecretKeySpec(key.data, 0, key.data.size, KEY_ALGORITHM)

            // Create random iv
            val iv = ByteArray(IV_SIZE)
            SecureRandom().nextBytes(iv)

            // Init the encryption
            cipher.init(Cipher.ENCRYPT_MODE, symmetricAESKey, IvParameterSpec(iv))

            // we append the IV to the beginning of the file data
            val outputBuffer = outputSink.buffer()
            outputBuffer.write(cipher.iv)
            outputBuffer.flush()

            // Encrypt and write the data to given outputPath
            outputBuffer.cipherSink(cipher).buffer().use { cipheredSink ->
                encryptedDataSize = cipheredSink.writeAll(assetDataSource)
                cipheredSink.flush()
            }
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while encrypting the asset:\n $e}")
        } finally {
            assetDataSource.close()
            outputSink.close()
        }
        return sizeWithPaddingAndIV(encryptedDataSize)
    }

    private fun sizeWithPaddingAndIV(size: Long): Long {
        // IV + data + pkcs7 padding
        return IV_SIZE + size + (AES_BLOCK_SIZE - (size % AES_BLOCK_SIZE))
    }

    internal fun encryptData(assetData: PlainData, key: AES256Key): EncryptedData {
        // Fetch AES256 Algorithm
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

        // Parse Secret Key from our custom AES256Key model object
        val symmetricAESKey = SecretKeySpec(key.data, 0, key.data.size, KEY_ALGORITHM)

        // Create random iv
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        // Do the encryption
        cipher.init(Cipher.ENCRYPT_MODE, symmetricAESKey, IvParameterSpec(iv))
        val cipherData = cipher.doFinal(assetData.data)

        // We prefix the first 16 bytes of the final encoded array with the Initialization Vector
        return EncryptedData(cipher.iv + cipherData)
    }

    internal fun generateRandomAES256Key(): AES256Key {
        // AES256 Symmetric secret key generation
        val keygen = KeyGenerator.getInstance(KEY_ALGORITHM)
        keygen.init(AES_KEYGEN_SIZE)
        return AES256Key(keygen.generateKey().encoded)
    }
}

internal class AESDecrypt(private val secretKey: AES256Key) {

    @Suppress("TooGenericExceptionCaught")
    internal fun decryptFile(encryptedDataSource: Source, outputSink: Sink): Long {
        var size = 0L
        try {
            // Fetch AES256 Algorithm
            val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

            // Parse Secret Key from our custom AES256Key model object
            val symmetricAESKey = SecretKeySpec(secretKey.data, 0, secretKey.data.size, KEY_ALGORITHM)

            // Read the first 16 bytes to get the IV
            val buffer = Buffer()
            encryptedDataSource.read(buffer, IV_SIZE.toLong())
            val iv = buffer.readByteArray()

            // Init the decryption
            cipher.init(Cipher.DECRYPT_MODE, symmetricAESKey, IvParameterSpec(iv))

            // Decrypt and write the data to given outputPath
            encryptedDataSource.cipherSource(cipher).buffer().use { bufferedSource ->
                val data = bufferedSource.readByteArray()
                size = data.size.toLong()
                outputSink.buffer().use {
                    it.write(data)
                    it.flush()
                }
            }
            kaliumLogger.d("WROTE $size bytes")
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while decrypting the asset:\n $e}")
        } finally {
            encryptedDataSource.close()
            outputSink.close()
        }
        return size
    }

    internal fun decryptData(encryptedData: EncryptedData): PlainData {
        // Fetch AES256 Algorithm
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

        // Parse Secret Key from our custom AES256Key model object
        val symmetricAESKey = SecretKeySpec(secretKey.data, 0, secretKey.data.size, KEY_ALGORITHM)

        // Get first 16 as they belong to the IV
        val iv = encryptedData.data.copyOfRange(0, IV_SIZE)

        // Do the decryption
        cipher.init(Cipher.DECRYPT_MODE, symmetricAESKey, IvParameterSpec(iv))
        val decryptedData = cipher.doFinal(encryptedData.data)

        return PlainData(decryptedData.copyOfRange(IV_SIZE, decryptedData.size))
    }
}

private const val KEY_ALGORITHM = "AES"
private const val KEY_ALGORITHM_CONFIGURATION = "AES/CBC/PKCS5PADDING"
private const val IV_SIZE = 16
private const val AES_BLOCK_SIZE = 16
private const val AES_KEYGEN_SIZE = 256
