/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.util.encodeBase64
import okio.Buffer
import okio.HashingSink
import okio.Sink
import okio.Source
import okio.blackholeSink
import okio.buffer
import okio.use

fun calcMd5(bytes: ByteArray): String =
    Buffer().use { it.write(bytes).md5().toByteArray().encodeBase64() }

/**
 * Method used to calculate the digested MD5 hash of a relatively small byte array
 *
 * @param bytes the data to be hashed
 * @return the digested md5 hash of the input [bytes] data
 * @see calcFileMd5
 */
@Suppress("TooGenericExceptionCaught")
fun calcFileMd5(dataSource: Source): String? =
    try {
        dataSource.buffer().peek().use { source ->
            HashingSink.md5(blackholeSink()).use { sink ->
                source.readAll(sink)
                sink.hash.toByteArray().encodeBase64()
            }
        }
    } catch (e: Exception) {
        kaliumLogger.e("There was an error while calculating the md5")
        null
    }

fun calcSHA256(bytes: ByteArray): ByteArray =
    Buffer().use { it.write(bytes).sha256().toByteArray() }

/**
 * Method used to calculate the digested SHA256 hash of a relatively small byte array
 *
 * @param bytes the data to be hashed
 * @return the digested SHA256 hash of the input [bytes] data
 * @see calcFileSHA256
 */
@Suppress("TooGenericExceptionCaught")
fun calcFileSHA256(dataSource: Source): ByteArray? =
    try {
        dataSource.buffer().peek().use { source ->
            HashingSink.sha256(blackholeSink()).use { sink ->
                source.readAll(sink)
                sink.hash.toByteArray()
            }
        }
    } catch (e: Exception) {
        kaliumLogger.e("There was an error while calculating the SHA256")
        null
    }

/**
 * Method used to encrypt a relatively small array of bytes using the AES256 encryption algorithm
 *
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
 *
 * @param data the [EncryptedData] that needs to be decrypted
 * @return the decrypted data as a byte array encapsulated in a [PlainData] object
 * @see decryptFileWithAES256
 */
expect fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData

/**
 * Method used to encrypt binary data using the AES256 encryption algorithm
 *
 * @param source the [Source] of the plain text data that needs to be encrypted
 * @param key the symmetric secret [AES256Key] that will be used for the encryption
 * @param sink the [Sink] which the encrypted data will be written to
 * @return the size of the encrypted data in bytes if the encryption succeeded and 0 otherwise
 * @see encryptDataWithAES256
 */
expect fun encryptFileWithAES256(source: Source, key: AES256Key, sink: Sink): Long

/**
 * Method used to decrypt some binary data using the AES256 encryption algorithm
 *
 * @param source the [Source] of the encrypted data that needs to be decrypted
 * @param sink the [Sink] to which the plain text data will be written to
 * @param secretKey the key used for the decryption
 * @return the size of the decrypted data in bytes if the decryption succeeded -1L otherwise
 * @see decryptDataWithAES256
 */
expect fun decryptFileWithAES256(source: Source, sink: Sink, secretKey: AES256Key): Long

/**
 * Method to generate a random Secret Key via the AES256 ciphering Algorithm
 * @return the AES256 secret key encapsulated in a [AES256Key] object
 */
expect fun generateRandomAES256Key(): AES256Key
