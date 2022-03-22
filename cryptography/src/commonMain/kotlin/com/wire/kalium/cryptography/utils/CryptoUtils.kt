package com.wire.kalium.cryptography.utils

expect fun calcMd5(bytes: ByteArray): String

/**
 * Method used to encrypt an array of bytes using the AES256 encryption algorithm
 * @param data the raw data that needs to be encrypted
 * @return a tuple containing the decrypted data in the first position,
 * and the symmetric secret key used for the encryption on the second position
 */
expect fun encryptDataWithAES256(data: ByteArray): Pair<ByteArray, SymmetricSecretKey>

/**
 * Method used to decrypt an array of bytes using the AES256 encryption algorithm
 * @param data the encrypted data that needs to be decrypted
 * @return the decrypted data as a byte array
 */
expect fun decryptDataWithAES256(data: ByteArray, secretKey: SymmetricSecretKey): ByteArray
