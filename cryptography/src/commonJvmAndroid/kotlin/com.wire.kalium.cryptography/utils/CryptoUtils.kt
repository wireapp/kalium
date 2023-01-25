/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import okio.HashingSink
import okio.Sink
import okio.Source
import okio.blackholeSink
import okio.buffer
import java.security.MessageDigest

actual fun calcMd5(bytes: ByteArray): String = bytes.let {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes, 0, it.size)
    val hash = md.digest()
    return hash.encodeBase64()
}

actual fun calcSHA256(bytes: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(bytes)
}

@Suppress("TooGenericExceptionCaught")
actual fun calcFileMd5(dataSource: Source): String? =
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

@Suppress("TooGenericExceptionCaught")
actual fun calcFileSHA256(dataSource: Source): ByteArray? =
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

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData = AESEncrypt().encryptData(data, key)

actual fun encryptFileWithAES256(assetDataSource: Source, key: AES256Key, outputSink: Sink) =
    AESEncrypt().encryptFile(assetDataSource, key, outputSink)

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData = AESDecrypt(secretKey).decryptData(data)

actual fun decryptFileWithAES256(encryptedDataSource: Source, decryptedDataSink: Sink, secretKey: AES256Key) =
    AESDecrypt(secretKey).decryptFile(encryptedDataSource, decryptedDataSink)

actual fun generateRandomAES256Key(): AES256Key = AESEncrypt().generateRandomAES256Key()
