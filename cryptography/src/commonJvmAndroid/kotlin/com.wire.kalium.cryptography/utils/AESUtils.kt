/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
                val contentBuffer = Buffer()
                var byteCount: Long
                while (assetDataSource.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
                    encryptedDataSize += byteCount
                    cipheredSink.write(contentBuffer, byteCount)
                    cipheredSink.flush()
                }
            }
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while encrypting the asset with AES256:\n $e}")
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
                val source = bufferedSource.inputStream().source()
                val contentBuffer = Buffer()
                var byteCount: Long
                while (source.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
                    size += byteCount
                    outputSink.write(contentBuffer, byteCount)
                    outputSink.flush()
                }
                source.close()
            }
            kaliumLogger.d("WROTE $size bytes")
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while decrypting the asset with AES256:\n $e}")
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
private const val BUFFER_SIZE = 1024 * 8L
