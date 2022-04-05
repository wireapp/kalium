package com.wire.kalium.cryptography.utils

import io.ktor.util.encodeBase64
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

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData = AESEncrypt().encrypt(data, key)

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData = AESDecrypt(secretKey).decrypt(data)

actual fun generateRandomAES256Key(): AES256Key = AESEncrypt().generateRandomAES256Key()
