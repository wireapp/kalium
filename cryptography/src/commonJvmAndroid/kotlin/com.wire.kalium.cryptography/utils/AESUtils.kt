package com.wire.kalium.cryptography.utils

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class AESEncrypt {

    internal fun encrypt(assetData: PlainData, key: AES256Key): EncryptedData {
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

    internal fun decrypt(encryptedData: EncryptedData): PlainData {
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
