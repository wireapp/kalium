package com.wire.kalium.cryptography.utils

import okio.FileSystem
import okio.Path
import kotlin.jvm.JvmInline

expect fun calcMd5(bytes: ByteArray): String

expect fun calcSHA256(bytes: ByteArray): ByteArray

/**
 * Method used to encrypt an array of bytes using the AES256 encryption algorithm on the given [encryptedDataPath]
 * @param data the [PlainData] that needs to be encrypted
 * @param key the symmetric secret [AES256Key] that will be used for the encryption
 * @param encryptedDataPath the path where the encrypted data will be saved
 * @param kaliumFileSystem the file system used to store the encrypted data
 * @return true if the encryption succeeded false otherwise
 */
expect fun encryptDataWithAES256(
    data: PlainData, key: AES256Key = generateRandomAES256Key(), encryptedDataPath: Path, kaliumFileSystem: FileSystem
): Boolean

/**
 * Method used to decrypt an array of bytes using the AES256 encryption algorithm
 * @param encryptedDataPath the encrypted data that needs to be decrypted
 * @param decryptedDataPath the encrypted data that needs to be decrypted
 * @param secretKey the key used for the decryption
 * @param kaliumFileSystem the file system used to store the encrypted data
 * @return true if the encryption succeeded false otherwise
 */
expect fun decryptDataWithAES256(
    encryptedDataPath: Path,
    decryptedDataPath: Path,
    secretKey: AES256Key,
    kaliumFileSystem: FileSystem
): Boolean

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
