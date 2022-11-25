package com.wire.kalium.util.string

actual fun String.toUTF16BEByteArray(): ByteArray {
    return toByteArray(charset = Charsets.UTF_16BE)
}

actual fun ByteArray.toStringFromUtf16BE(): String {
    return toString(charset = Charsets.UTF_16BE)
}
