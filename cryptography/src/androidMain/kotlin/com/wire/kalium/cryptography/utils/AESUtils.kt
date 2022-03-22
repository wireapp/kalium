package com.wire.kalium.cryptography.utils

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

internal class AESEncrypt {

    internal fun encrypt(unencryptedData: ByteArray): Pair<ByteArray, SecretKey?> {

        // Secret symmetric key generation
        val keygen = KeyGenerator.getInstance(KEY_ALGORITHM)
        keygen.init(256)
        val key = keygen.generateKey()

        val cipher = Cipher.getInstance(KEY_ALGORITHM_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val cipherData = cipher.doFinal(unencryptedData)
        val finalEncodedArray = cipher.iv + cipherData

        return finalEncodedArray to key
    }
}

class AESDecrypt(private val secretKey: ByteArray) {

    fun decrypt(encryptedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(KEY_ALGORITHM_PADDING)
//        val ivSpec = IvParameterSpec(initializationVector)
//        cipher.init(Cipher.DECRYPT_MODE, mySecretKey, ivSpec)

        val symmetricAESKey = SecretKeySpec(secretKey, 0, secretKey.size, KEY_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, symmetricAESKey)

        val sanitizedData = encryptedData.copyOfRange(16, encryptedData.size)

        return cipher.doFinal(sanitizedData)
    }

}

private const val KEY_ALGORITHM = "AES"
private const val KEY_ALGORITHM_PADDING = "AES/CBC/PKCS5PADDING"
