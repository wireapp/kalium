package com.wire.kalium.cryptography.utils

import kotlin.jvm.JvmInline

expect fun calcMd5(bytes: ByteArray): String

/**
 * Method used to encrypt an array of bytes using the AES256 encryption algorithm
 * @param data the raw data that needs to be encrypted
 * @return a tuple containing the decrypted data in the first position,
 * and the symmetric secret key used for the encryption on the second position
 */
expect fun encryptDataWithAES256(data: PlainData, key: AES256Key = generateRandomAES256Key()): EncryptedData

/**
 * Method used to decrypt an array of bytes using the AES256 encryption algorithm
 * @param data the encrypted data that needs to be decrypted
 * @return the decrypted data as a byte array
 */
expect fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData

expect fun generateRandomAES256Key(): AES256Key

@JvmInline
value class AES256Key(val data: ByteArray)

@JvmInline
value class EncryptedData(val data: ByteArray)

@JvmInline
value class PlainData(val data: ByteArray)
