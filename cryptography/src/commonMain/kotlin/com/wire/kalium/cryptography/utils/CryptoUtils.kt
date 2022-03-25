package com.wire.kalium.cryptography.utils

import kotlin.jvm.JvmInline

expect fun calcMd5(bytes: ByteArray): String

/**
 * Method used to encrypt an array of bytes using the AES256 encryption algorithm
 * @param data the [PlainData] that needs to be encrypted
 * @param key the symmetric secret [AES256Key] that will be used for the encryption
 * @return the final [EncryptedData], on which the first 16 bytes belong to the initialisation vector
 */
expect fun encryptDataWithAES256(data: PlainData, key: AES256Key = generateRandomAES256Key()): EncryptedData

/**
 * Method used to decrypt an array of bytes using the AES256 encryption algorithm
 * @param data the [EncryptedData] that needs to be decrypted
 * @return the decrypted data as a byte array encapsulated in a [PlainData] object
 */
expect fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData

/**
 * Method to generate a random Secret Key via the AES256 ciphering Algorithm
 * @return the AES256 secret key encapsulated in a [AES256Key] object
 */
expect fun generateRandomAES256Key(): AES256Key

@JvmInline
value class AES256Key(val data: ByteArray)

@JvmInline
value class EncryptedData(val data: ByteArray)

@JvmInline
value class PlainData(val data: ByteArray)
