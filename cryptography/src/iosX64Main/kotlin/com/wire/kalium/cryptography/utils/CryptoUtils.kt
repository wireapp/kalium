package com.wire.kalium.cryptography.utils

import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.base64Encoding
import platform.Foundation.create

actual fun calcMd5(dataPath: Path, kaliumFileSystem: FileSystem): String {
    val digestData = UByteArray(CC_MD5_DIGEST_LENGTH)
    val bytes = kaliumFileSystem.source(dataPath).use {
        it.buffer().use { bufferedFileSource ->
            bufferedFileSource.readByteArray()
        }
    }
    val data = toData(bytes)
    CC_MD5(data.bytes, data.length.toUInt(), digestData.refTo(0))

    return toData(digestData.asByteArray()).base64Encoding()
}

actual fun calcSHA256(dataPath: Path, kaliumFileSystem: FileSystem): ByteArray {
    TODO("Not yet implemented")
}

private fun toData(data: ByteArray): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(data), length = data.size.toULong())
}

actual fun encryptDataWithAES256(unencryptedDataPath: Path, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem): Long {
    TODO("Not yet implemented")
}

actual fun decryptDataWithAES256(
    encryptedDataPath: Path,
    decryptedDataPath: Path,
    secretKey: AES256Key,
    kaliumFileSystem: FileSystem
): Boolean {
    TODO("Not yet implemented")
}

actual fun generateRandomAES256Key(): AES256Key {
    TODO("Not yet implemented")
}
