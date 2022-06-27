package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.util.*
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import okio.*
import platform.Foundation.NSData
import platform.Foundation.create

actual fun calcMd5(dataPath: Path, kaliumFileSystem: FileSystem): String? =
    try {
        kaliumFileSystem.source(dataPath).buffer().use { source ->
            HashingSink.md5(blackholeSink()).use { sink ->
                source.readAll(sink)
                sink.hash.toByteArray().encodeBase64()
            }
        }
    } catch (e: Exception) {
        kaliumLogger.e("There was an error while calculating the md5")
        null
    }

actual fun calcSHA256(dataPath: Path, kaliumFileSystem: FileSystem): ByteArray? =
    try {
        kaliumFileSystem.source(dataPath).buffer().use { source ->
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

actual fun encryptDataWithAES256(rawDataPath: Path, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem): Long {
    TODO("Not yet implemented")
}

actual fun decryptDataWithAES256(
    encryptedDataSource: Source,
    decryptedDataPath: Path,
    secretKey: AES256Key,
    kaliumFileSystem: FileSystem
): Long {
    TODO("Not yet implemented")
}

actual fun generateRandomAES256Key(): AES256Key {
    TODO("Not yet implemented")
}
