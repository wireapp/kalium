package com.wire.kalium.util.string

import kotlinx.cinterop.getBytes
import kotlinx.cinterop.utf16
import platform.Foundation.NSString
import platform.Foundation.NSUnitInformationStorage.Companion.bytes

actual fun String.toUTF16BEByteArray(): ByteArray {
    return utf16.getBytes()
}
actual fun ByteArray.toStringFromUtf16BE(): String {
    TODO("Not yet implemented")
}
