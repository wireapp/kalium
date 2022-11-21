package com.wire.kalium.util.string

actual fun String.toUTF16BEByteArray(): ByteArray {
    return toByteArray(charset = Charsets.UTF_16BE)
}

actual fun ByteArray.toStringFromUtf16BE(): String {
    return toString(charset = Charsets.UTF_16BE)
}

actual fun ByteArray.toHexString(): String {
    return joinToString("") { (0xFF and it.toInt()).toString(16).padStart(2, '0') }
}

actual fun ByteArray.toStringFromUtf8(): String {
    return toString(charset = Charsets.UTF_8)
}

actual fun Long.toByteArray(): ByteArray {
    var l = this
    val result = ByteArray(java.lang.Long.BYTES)
    for (i in java.lang.Long.BYTES - 1 downTo 0) {
        result[i] = (l and 0xFFL).toByte()
        l = l shr java.lang.Byte.SIZE
    }
    return result
}


