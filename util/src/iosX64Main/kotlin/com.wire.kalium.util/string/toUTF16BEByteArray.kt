package com.wire.kalium.util.string

import kotlinx.cinterop.getBytes
import kotlinx.cinterop.utf16

actual fun String.toUTF16BEByteArray(): ByteArray {
    return utf16.getBytes()
}
