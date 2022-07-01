package com.wire.kalium.cryptography.utils

import okio.FileSystem
import okio.Path
import okio.Source
import kotlin.jvm.JvmInline

/**
 * Method used to calculate the digested MD5 hash of a relatively small byte array
 * @param bytes the data to be hashed
 * @return the digested md5 hash of the input data
 */
expect fun calcMd5(bytes: ByteArray): String

/**
 * Method used to calculate the digested SHA256 hash of a relatively small byte array
 * @param bytes the data to be hashed
 * @return the digested SHA256 hash of the input data
 */
expect fun calcSHA256(bytes: ByteArray): ByteArray

/**
 * Method used to calculate the digested MD5 hash of a given file
 * @param dataPath the file containing the data to be hashed
 * @param kaliumFileSystem the file system used to access the stored data
 * @return the digested md5 hash of the input data
 */
expect fun calcFileMd5(dataPath: Path, kaliumFileSystem: FileSystem): String?

/**
 * Method used to calculate the digested MD5 hash of a relatively small byte array
 * @param bytes the data to be hashed
 * @return the digested md5 hash of the input data
 */
expect fun calcFileSHA256(dataPath: Path, kaliumFileSystem: FileSystem): ByteArray?

/**
 * Method used to encrypt a relatively small array of bytes using the AES256 encryption algorithm
 * @param data the [PlainData] that needs to be encrypted
 * @param key the symmetric secret [AES256Key] that will be used for the encryption
 * @return the final [EncryptedData], on which the first 16 bytes belong to the initialisation vector
 */
expect fun encryptDataWithAES256(
    data: PlainData,
    key: AES256Key = generateRandomAES256Key()
): EncryptedData

/**
 * Method used to decrypt a relatively small array of bytes using the AES256 decryption algorithm
 * @param data the [EncryptedData] that needs to be decrypted
 * @return the decrypted data as a byte array encapsulated in a [PlainData] object
 */
expect fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData

/**
 * Method used to encrypt some data stored on the file system using the AES256 encryption algorithm
 * @param rawDataPath the path to the data that needs to be encrypted
 * @param key the symmetric secret [AES256Key] that will be used for the encryption
 * @param encryptedDataPath the path where the encrypted data will be saved
 * @param kaliumFileSystem the file system used to store the encrypted data
 * @return the size of the encrypted data in bytes if the encryption succeeded and 0 otherwise
 */
expect fun encryptFileWithAES256(
    rawDataPath: Path,
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
expect fun decryptFileWithAES256(
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
value class SHA256Key(val data: ByteArray)

@JvmInline
value class AES256Key(val data: ByteArray)

@JvmInline
value class EncryptedData(val data: ByteArray)

@JvmInline
value class PlainData(val data: ByteArray)
