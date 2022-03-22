package com.wire.kalium.cryptography.utils

import io.ktor.util.*
import java.security.MessageDigest

actual fun calcMd5(bytes: ByteArray): String = bytes.let {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes, 0, it.size)
    val hash = md.digest()
    return hash.encodeBase64()
}

actual fun encryptDataWithAES256(data: ByteArray): ByteArray {
    val (encryptedData, secretKey) = AESEncrypt().encrypt(data)
    return encryptedData
}

actual fun decryptDataWithAES256(data: ByteArray, secretKey: ByteArray): ByteArray = AESDecrypt(secretKey).decrypt(data)
