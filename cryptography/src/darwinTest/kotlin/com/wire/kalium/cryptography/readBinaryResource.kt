package com.wire.kalium.cryptography

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.lastPathComponent
import platform.Foundation.pathExtension
import platform.Foundation.stringByDeletingPathExtension
import platform.posix.memcpy

/** Read the given resource as binary data. */
@Suppress("MagicNumber")
actual fun readBinaryResource(
    resourceName: String
): ByteArray {
    val name = ((resourceName as NSString).lastPathComponent as NSString).stringByDeletingPathExtension
    val pathExtension =  (resourceName as NSString).pathExtension
    val path = NSBundle.mainBundle.pathForResource("resources/$name", pathExtension)
    val data = NSData.dataWithContentsOfFile(path!!)
    return data!!.toByteArray()
}

internal fun NSData.toByteArray(): ByteArray {
    return ByteArray(length.toInt()).apply {
        usePinned {
            memcpy(it.addressOf(0), bytes, length)
        }
    }
}
