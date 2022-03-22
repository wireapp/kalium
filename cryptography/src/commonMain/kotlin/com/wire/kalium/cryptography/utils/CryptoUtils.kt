package com.wire.kalium.cryptography.utils

expect fun calcMd5(bytes: ByteArray): String

expect fun encryptDataWithAES256(data: ByteArray): Pair<ByteArray,ByteArray>

expect fun decryptDataWithAES256(data: ByteArray, secretKey: ByteArray): ByteArray
