package com.wire.kalium.util.string

actual fun String.toUTF16BEByteArray(): ByteArray {
    return toByteArray(charset = Charsets.UTF_16BE)
}
