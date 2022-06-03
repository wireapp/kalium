package com.wire.kalium.cryptography.utils

import io.ktor.util.*
import okio.FileSystem
import okio.Path
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

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key, encryptedDataPath: Path, kaliumFileSystem: FileSystem) =
    AESEncrypt().encrypt(data, key, encryptedDataPath, kaliumFileSystem)

actual fun decryptDataWithAES256(encryptedDataPath: Path, decryptedDataPath: Path, secretKey: AES256Key, kaliumFileSystem: FileSystem) =
    AESDecrypt(secretKey).decrypt(encryptedDataPath, decryptedDataPath, kaliumFileSystem)

actual fun generateRandomAES256Key(): AES256Key = AESEncrypt().generateRandomAES256Key()
