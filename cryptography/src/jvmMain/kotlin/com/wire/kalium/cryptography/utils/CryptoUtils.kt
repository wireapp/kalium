package com.wire.kalium.cryptography.utils

import io.ktor.util.encodeBase64
import java.security.MessageDigest

actual fun calcMd5(bytes: ByteArray): String = bytes.let {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes, 0, it.size)
    val hash = md.digest()
    return hash.encodeBase64()
}

actual fun encryptDataWithAES256(data: ByteArray): Pair<ByteArray, SymmetricSecretKey> {
    val (encryptedData, secretKey) = AESEncrypt().encrypt(data)
    return encryptedData to secretKey
}

actual fun decryptDataWithAES256(data: ByteArray, secretKey: SymmetricSecretKey): ByteArray = AESDecrypt(secretKey).decrypt(data)
