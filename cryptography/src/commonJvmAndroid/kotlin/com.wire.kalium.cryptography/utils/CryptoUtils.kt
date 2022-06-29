package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.util.encodeBase64
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.Source
import okio.blackholeSink
import okio.buffer
import java.security.MessageDigest
import kotlin.io.use

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

actual fun calcFileMd5(dataPath: Path, kaliumFileSystem: FileSystem): String? =
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

actual fun calcFileSHA256(dataPath: Path, kaliumFileSystem: FileSystem): ByteArray? =
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

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData = AESEncrypt().encryptData(data, key)

actual fun encryptFileWithAES256(rawDataPath: Path, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem) =
    AESEncrypt().encryptFile(rawDataPath, key, encryptedDataPath, kaliumFileSystem)

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData = AESDecrypt(secretKey).decryptData(data)

actual fun decryptFileWithAES256(encryptedDataSource: Source, decryptedDataPath: Path, secretKey: AES256Key, kaliumFileSystem: FileSystem) =
    AESDecrypt(secretKey).decryptFile(encryptedDataSource, decryptedDataPath, kaliumFileSystem)

actual fun generateRandomAES256Key(): AES256Key = AESEncrypt().generateRandomAES256Key()
