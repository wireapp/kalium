package com.wire.kalium.util.string

import kotlinx.cinterop.getBytes
import kotlinx.cinterop.utf16

actual fun String.toUTF16BEByteArray(): ByteArray {
    return utf16.getBytes()
}

actual fun ByteArray.toStringFromUtf16BE(): String {
    TODO("Not yet implemented")
}

actual fun ByteArray.toHexString(): String {
    return joinToString("") { (0xFF and it.toInt()).toString(16).padStart(2, '0') }
}

actual fun Long.to16BitHexString(): String {
    TODO("Not yet implemented")
}
