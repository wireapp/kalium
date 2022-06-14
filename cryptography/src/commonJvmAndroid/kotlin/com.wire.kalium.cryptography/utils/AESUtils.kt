package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.cipherSink
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.use

internal class AESEncrypt {

    internal fun encrypt(assetDataPath: Path, key: AES256Key, outputPath: Path, kaliumFileSystem: FileSystem): Boolean {
        return try {
            // Fetch AES256 Algorithm
            val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

            // Parse Secret Key from our custom AES256Key model object
            val symmetricAESKey = SecretKeySpec(key.data, 0, key.data.size, KEY_ALGORITHM)

            val iv = ByteArray(16)

            // Init the encryption
            cipher.init(Cipher.ENCRYPT_MODE, symmetricAESKey, IvParameterSpec(iv))

            // Encrypt and write the data to given outputPath
            val cipherSink = kaliumFileSystem.sink(outputPath).cipherSink(cipher)
            cipherSink.buffer().use { sink ->
                sink.writeAll(kaliumFileSystem.source(assetDataPath))
            }
            true
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while encrypting the asset:\n $e}")
            false
        }
    }

    internal fun generateRandomAES256Key(): AES256Key {
        // AES256 Symmetric secret key generation
        val keygen = KeyGenerator.getInstance(KEY_ALGORITHM)
        keygen.init(256)
        return AES256Key(keygen.generateKey().encoded)
    }
}

internal class AESDecrypt(private val secretKey: AES256Key) {

    internal fun decrypt(encryptedDataPath: Path, outputPath: Path, kaliumFileSystem: FileSystem): Boolean {
        return try {
            // Fetch AES256 Algorithm
            val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

            // Parse Secret Key from our custom AES256Key model object
            val symmetricAESKey = SecretKeySpec(secretKey.data, 0, secretKey.data.size, KEY_ALGORITHM)

            // Init the decryption
            cipher.init(Cipher.DECRYPT_MODE, symmetricAESKey, IvParameterSpec(ByteArray(16)))

            // Decrypt and write the data to given outputPath
            val source = kaliumFileSystem.source(encryptedDataPath)
            val cipherSource = source.cipherSource(cipher)
            cipherSource.buffer().use { bufferedSource ->
                kaliumFileSystem.write(outputPath) {
                    write(bufferedSource.readByteArray())
                }
            }
            true
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while decrypting the asset:\n $e}")
            false
        }
    }
}

private const val KEY_ALGORITHM = "AES"
private const val KEY_ALGORITHM_CONFIGURATION = "AES/CBC/PKCS5PADDING"
