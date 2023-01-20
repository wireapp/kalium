package com.wire.kalium.util.string

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF16BigEndianStringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.getBytes

actual fun String.toUTF16BEByteArray(): ByteArray =
    (this as NSString).dataUsingEncoding(NSUTF16BigEndianStringEncoding)!!.toByteArray()

actual fun ByteArray.toStringFromUtf16BE(): String = memScoped {
    val data = NSData.create(
        bytes = allocArrayOf(this@toStringFromUtf16BE),
        length = this@toStringFromUtf16BE.size.toULong()
    )
    val string = NSString.create(data = data, encoding = NSUTF16BigEndianStringEncoding)
    return@memScoped string as String
}

fun NSData.toByteArray(): ByteArray {
    val buffer = ByteArray(length.toInt())
    buffer.usePinned {
        getBytes(it.addressOf(0))
    }
    return buffer
}
