package com.wire.kalium.cryptography

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/** Read the given resource as binary data. */
actual fun readBinaryResource(
    resourceName: String
): ByteArray {
    // split based on "." and "/". We want to strip the leading ./ and
    // split the extension
    val pathParts = resourceName.split("[.|/]".toRegex())
    // pathParts looks like
    // [, , test_case_input_one, bin]
    val path = NSBundle.mainBundle
        .pathForResource("resources/${pathParts[2]}", pathParts[3])
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
