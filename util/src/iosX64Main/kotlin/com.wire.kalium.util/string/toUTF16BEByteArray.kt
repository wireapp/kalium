package com.wire.kalium.util.string

import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.getBytes
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.utf16
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF16BigEndianStringEncoding

actual fun String.toUTF16BEByteArray(): ByteArray {
    return utf16.getBytes()
}

actual fun ByteArray.toStringFromUtf16BE(): String = memScoped {
    val data = NSData.create(
        bytes = allocArrayOf(this@toStringFromUtf16BE),
        length = this@toStringFromUtf16BE.size.toULong()
    )
    val string = NSString.create(data = data, encoding = NSUTF16BigEndianStringEncoding)
    return@memScoped string
}

