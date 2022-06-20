package com.wire.kalium.cryptography.utils

import okio.FileSystem
import okio.Path
import okio.Source
import kotlin.jvm.JvmInline

expect fun calcMd5(dataPath: Path, kaliumFileSystem: FileSystem): String?

expect fun calcSHA256(dataPath: Path, kaliumFileSystem: FileSystem): ByteArray?

/**
 * Method used to encrypt an array of bytes using the AES256 encryption algorithm on the given [encryptedDataPath]
 * @param unencryptedDataPath the path to the data that needs to be encrypted
 * @param key the symmetric secret [AES256Key] that will be used for the encryption
 * @param encryptedDataPath the path where the encrypted data will be saved
 * @param kaliumFileSystem the file system used to store the encrypted data
 * @return the size of the encrypted data in bytes if the encryption succeeded and 0 otherwise
 */
expect fun encryptDataWithAES256(
    unencryptedDataPath: Path,
    key: AES256Key = generateRandomAES256Key(),
    encryptedDataPath: Path,
    kaliumFileSystem: FileSystem
): Long

/**
 * Method used to decrypt some binary data using the AES256 encryption algorithm
 * @param encryptedDataSource the [Source] of the encrypted data that needs to be decrypted
 * @param decryptedDataPath the encrypted data that needs to be decrypted
 * @param secretKey the key used for the decryption
 * @param kaliumFileSystem the file system used to store the encrypted data
 * @return the size of the decrypted data in bytes if the decryption succeeded -1L otherwise
 */
expect fun decryptDataWithAES256(
    encryptedDataSource: Source,
    decryptedDataPath: Path,
    secretKey: AES256Key,
    kaliumFileSystem: FileSystem
): Long

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
