package com.wire.kalium.util.string

expect fun String.toUTF16BEByteArray(): ByteArray

expect fun ByteArray.toStringFromUtf16BE(): String
fun ByteArray.toHexString(): String {
    return joinToString("") {
        (0xFF and it.toInt()).toString(16).padStart(2, '0')
    }
}
