package com.wire.kalium.util.string

import kotlin.math.roundToLong

actual fun String.toUTF16BEByteArray(): ByteArray {
    return toByteArray(charset = Charsets.UTF_16BE)
}

actual fun ByteArray.toStringFromUtf16BE(): String {
    return toString(charset = Charsets.UTF_16BE)
}

actual fun ByteArray.toHexString(): String {
    return joinToString("") { (0xFF and it.toInt()).toString(16).padStart(2, '0') }
}

actual fun Long.to16BitHexString(): String {
    val buffer = ByteArray(16)
    val hexStringByteArray = toString(16).toByteArray()

    for (byteIndex in 0..hexStringByteArray.size) {
        val offSet = buffer.size - byteIndex
        buffer[offSet] = hexStringByteArray[byteIndex]
    }

    return hexStringByteArray.toHexString()
}
