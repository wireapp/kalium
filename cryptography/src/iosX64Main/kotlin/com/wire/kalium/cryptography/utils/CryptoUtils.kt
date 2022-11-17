package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.util.encodeBase64
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.Sink
import okio.Source
import okio.blackholeSink
import okio.buffer
import okio.use
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.base64Encoding
import platform.Foundation.create

actual fun calcMd5(bytes: ByteArray): String {
    Charsets.ISO_8859_1
    val digestData = UByteArray(CC_MD5_DIGEST_LENGTH)
    val data = toData(bytes)
    CC_MD5(data.bytes, data.length.toUInt(), digestData.refTo(0))

    return toData(digestData.asByteArray()).base64Encoding()
}

actual fun calcSHA256(bytes: ByteArray): ByteArray {
    TODO("Not yet implemented")
}

@Suppress("TooGenericExceptionCaught")
actual fun calcFileMd5(dataSource: Source): String? =
    try {
        dataSource.buffer().use { source ->
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
        dataSource.buffer().use { source ->
            HashingSink.sha256(blackholeSink()).use { sink ->
                source.readAll(sink)
                sink.hash.toByteArray()
            }
        }
    } catch (e: Exception) {
        kaliumLogger.e("There was an error while calculating the SHA256")
        null
    }

private fun toData(data: ByteArray): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(data), length = data.size.toULong())
}

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData {
    TODO("Not yet implemented")
}

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData {
    TODO("Not yet implemented")
}

actual fun encryptFileWithAES256(assetDataSource: Source, key: AES256Key, outputSink: Sink): Long =
    TODO("Not yet implemented")


actual fun decryptFileWithAES256(encryptedDataSource: Source, decryptedDataSink: Sink, secretKey: AES256Key): Long =
    TODO("Not yet implemented")


actual fun generateRandomAES256Key(): AES256Key {
    TODO("Not yet implemented")
}
