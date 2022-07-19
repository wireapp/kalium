package com.wire.kalium.cryptography.utils

import okio.Sink
import okio.Source
import kotlin.jvm.JvmInline

/**
 * Method used to calculate the digested MD5 hash of a relatively small byte array
 * @param bytes the data to be hashed
 * @return the digested md5 hash of the input [bytes] data
 * @see calcFileMd5
 */
expect fun calcMd5(bytes: ByteArray): String

/**
 * Method used to calculate the digested SHA256 hash of a relatively small byte array
 * @param bytes the data to be hashed
 * @return the digested SHA256 hash of the input [bytes] data
 * @see calcFileSHA256
 */
expect fun calcSHA256(bytes: ByteArray): ByteArray

/**
 * Method used to calculate the digested MD5 hash of a given file
 * @param dataSource the data stream used to read the stored data and hash it
 * @return the digested md5 hash of the input [dataSource]
 * @see calcMd5
 */
expect fun calcFileMd5(dataSource: Source): String?

/**
 * Method used to calculate the digested MD5 hash of a given file
 * @param dataSource the data stream used to read the stored data and hash it
 * @return the digested md5 hash of the input [dataSource]
 * @see calcSHA256
 */
expect fun calcFileSHA256(dataSource: Source): ByteArray?

/**
 * Method used to encrypt a relatively small array of bytes using the AES256 encryption algorithm
 * @param data the [PlainData] that needs to be encrypted
 * @param key the symmetric secret [AES256Key] that will be used for the encryption
 * @return the final [EncryptedData], on which the first 16 bytes belong to the initialisation vector
 * @see encryptFileWithAES256
 */
expect fun encryptDataWithAES256(
    data: PlainData,
    key: AES256Key = generateRandomAES256Key()
): EncryptedData

/**
 * Method used to decrypt a relatively small array of bytes using the AES256 decryption algorithm
 * @param data the [EncryptedData] that needs to be decrypted
 * @return the decrypted data as a byte array encapsulated in a [PlainData] object
 * @see decryptFileWithAES256
 */
expect fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData

/**
 * Method used to encrypt some data stored on the file system using the AES256 encryption algorithm
 * @param assetDataSource the path to the data that needs to be encrypted
 * @param key the symmetric secret [AES256Key] that will be used for the encryption
 * @param outputSink the path where the encrypted data will be saved
 * @return the size of the encrypted data in bytes if the encryption succeeded and 0 otherwise
 * @see encryptDataWithAES256
 */
expect fun encryptFileWithAES256(assetDataSource: Source, key: AES256Key, outputSink: Sink): Long

/**
 * Method used to decrypt some binary data using the AES256 encryption algorithm
 * @param encryptedDataSource the [Source] of the encrypted data that needs to be decrypted
 * @param decryptedDataSink the output stream data sink invoked to write the decrypted data
 * @param secretKey the key used for the decryption
 * @return the size of the decrypted data in bytes if the decryption succeeded -1L otherwise
 * @see decryptDataWithAES256
 */
expect fun decryptFileWithAES256(encryptedDataSource: Source, decryptedDataSink: Sink, secretKey: AES256Key): Long

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
