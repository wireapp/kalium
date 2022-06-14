package com.wire.kalium.cryptography.utils

import io.ktor.util.*
import okio.FileSystem
import okio.Path
import java.security.MessageDigest

actual fun calcMd5(dataPath: Path, kaliumFileSystem: FileSystem): String {
    val md = MessageDigest.getInstance("MD5")
//    md.update(bytes, 0, it.size)
    val hash = calculateFileChecksum(md, dataPath.toFile())
    return hash.encodeBase64()
}

actual fun calcSHA256(dataPath: Path): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    return calculateFileChecksum(md, dataPath.toFile())
}

actual fun encryptDataWithAES256(unencryptedDataPath: Path, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem) =
    AESEncrypt().encrypt(unencryptedDataPath, key, encryptedDataPath, kaliumFileSystem)

actual fun decryptDataWithAES256(encryptedDataPath: Path, decryptedDataPath: Path, secretKey: AES256Key, kaliumFileSystem: FileSystem) =
    AESDecrypt(secretKey).decrypt(encryptedDataPath, decryptedDataPath, kaliumFileSystem)

actual fun generateRandomAES256Key(): AES256Key = AESEncrypt().generateRandomAES256Key()
