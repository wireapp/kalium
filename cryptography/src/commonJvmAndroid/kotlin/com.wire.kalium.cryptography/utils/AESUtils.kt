package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import okio.FileSystem
import okio.Path
import okio.Source
import okio.buffer
import okio.cipherSink
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.use

internal class AESEncrypt {

    internal fun encryptFile(assetDataPath: Path, key: AES256Key, outputPath: Path, kaliumFileSystem: FileSystem): Long {
        var encryptedDataSize = 0L
        try {
        // Fetch AES256 Algorithm
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)
        var encryptedDataWithIVSize = 0L

        // Parse Secret Key from our custom AES256Key model object
        val symmetricAESKey = SecretKeySpec(key.data, 0, key.data.size, KEY_ALGORITHM)

        // Init the encryption
        cipher.init(Cipher.ENCRYPT_MODE, symmetricAESKey)

        // Encrypt and write the data to given outputPath
        kaliumFileSystem.sink(outputPath).cipherSink(cipher).buffer().use { cipheredSink ->
            cipheredSink.write(cipher.iv) // we append the IV to the beginning of the file data
            encryptedDataSize = cipheredSink.writeAll(kaliumFileSystem.source(assetDataPath))
            kaliumLogger.d("** The encrypted data size is => $encryptedDataSize")
        }

        kaliumLogger.d("** The encrypted data with IV size is => ${sizeWithPaddingAndIV(encryptedDataSize)}")
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while encrypting the asset:\n $e}")
        }
        return sizeWithPaddingAndIV(encryptedDataSize)
    }

    private fun sizeWithPaddingAndIV(size: Long): Long = size + (32L - (size % 16L))

    internal fun encryptData(assetData: PlainData, key: AES256Key): EncryptedData {
        // Fetch AES256 Algorithm
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

        // Parse Secret Key from our custom AES256Key model object
        val symmetricAESKey = SecretKeySpec(key.data, 0, key.data.size, KEY_ALGORITHM)

        // Do the encryption
        cipher.init(Cipher.ENCRYPT_MODE, symmetricAESKey)
        val cipherData = cipher.doFinal(assetData.data)

        // We prefix the first 16 bytes of the final encoded array with the Initialization Vector
        return EncryptedData(cipher.iv + cipherData)
    }

    internal fun generateRandomAES256Key(): AES256Key {
        // AES256 Symmetric secret key generation
        val keygen = KeyGenerator.getInstance(KEY_ALGORITHM)
        keygen.init(256)
        return AES256Key(keygen.generateKey().encoded)
    }
}

internal class AESDecrypt(private val secretKey: AES256Key) {

    internal fun decryptFile(encryptedDataSource: Source, outputPath: Path, kaliumFileSystem: FileSystem): Long {
        var size = 0L
        try {
        // Fetch AES256 Algorithm
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

        // Parse Secret Key from our custom AES256Key model object
        val symmetricAESKey = SecretKeySpec(secretKey.data, 0, secretKey.data.size, KEY_ALGORITHM)

        // Init the decryption
        cipher.init(Cipher.DECRYPT_MODE, symmetricAESKey, IvParameterSpec(ByteArray(16)))

        // Decrypt and write the data to given outputPath
        encryptedDataSource.cipherSource(cipher).buffer().use { bufferedSource ->
            kaliumFileSystem.write(outputPath) {
                val dataWithIV = bufferedSource.readByteArray()
                val data = dataWithIV.copyOfRange(16, dataWithIV.size) // We discard the first 16 bytes corresponding to the IV
                size = data.size.toLong()
                write(data)
            }
        }
        kaliumLogger.d("WROTE $size bytes")
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while decrypting the asset:\n $e}")
        }
        return size
    }

    internal fun decryptData(encryptedData: EncryptedData): PlainData {
        // Fetch AES256 Algorithm
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

        // Parse Secret Key from our custom AES256Key model object
        val symmetricAESKey = SecretKeySpec(secretKey.data, 0, secretKey.data.size, KEY_ALGORITHM)

        // Do the decryption
        cipher.init(Cipher.DECRYPT_MODE, symmetricAESKey, IvParameterSpec(ByteArray(16)))
        val decryptedData = cipher.doFinal(encryptedData.data)

        // We ignore the first 16 bytes as they are reserved for the Initialization Vector
        return PlainData(decryptedData.copyOfRange(16, decryptedData.size))
    }
}

private const val KEY_ALGORITHM = "AES"
private const val KEY_ALGORITHM_CONFIGURATION = "AES/CBC/PKCS5PADDING"
