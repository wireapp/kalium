package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.util.encodeBase64
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.Source
import okio.blackholeSink
import okio.buffer
import kotlin.io.use

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

actual fun encryptDataWithAES256(rawDataPath: Path, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem) =
    AESEncrypt().encrypt(rawDataPath, key, encryptedDataPath, kaliumFileSystem)

actual fun decryptDataWithAES256(encryptedDataSource: Source, decryptedDataPath: Path, secretKey: AES256Key, kaliumFileSystem: FileSystem) =
    AESDecrypt(secretKey).decrypt(encryptedDataSource, decryptedDataPath, kaliumFileSystem)

actual fun generateRandomAES256Key(): AES256Key = AESEncrypt().generateRandomAES256Key()
