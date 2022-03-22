package com.wire.kalium.cryptography.utils

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class AESEncrypt {

    internal fun encrypt(unencryptedData: ByteArray): Pair<ByteArray, SymmetricSecretKey> {

        // Secret symmetric key generation
        val keygen = KeyGenerator.getInstance(KEY_ALGORITHM)
        keygen.init(256)
        val key = keygen.generateKey()

        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val cipherData = cipher.doFinal(unencryptedData)

        // We prefix the first 16 bytes of the final encoded array with the Initialization Vector
        val finalEncodedArray = cipher.iv + cipherData

        return finalEncodedArray to key.encoded
    }
}

internal class AESDecrypt(private val secretKey: ByteArray) {

    internal fun decrypt(encryptedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

        val symmetricAESKey = SecretKeySpec(secretKey, 0, secretKey.size, KEY_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, symmetricAESKey, IvParameterSpec(ByteArray(16)))

        val decryptedData = cipher.doFinal(encryptedData)

        // We ignore the first 16 bytes as they are reserved for the Initialization Vector
        return decryptedData.copyOfRange(16, decryptedData.size)
    }
}

private const val KEY_ALGORITHM = "AES"
private const val KEY_ALGORITHM_CONFIGURATION = "AES/CBC/PKCS5PADDING"
