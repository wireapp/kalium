/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
