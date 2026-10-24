/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.message.linkpreview

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData

@Suppress("ReturnCount")
internal actual fun readImageMetadata(bytes: ByteArray, fallbackMimeType: String): ImageMetadata? {
    if (bytes.isEmpty()) return null

    // The caller owns Core Foundation "Create" results; Kotlin/Native does not release them.
    val data = bytes.usePinned {
        CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong())
    } ?: return null
    val imageSource = CGImageSourceCreateWithData(data, null)
    val image = imageSource?.let { CGImageSourceCreateImageAtIndex(it, 0u, null) }
    return try {
        image?.let {
            ImageMetadata(
                width = CGImageGetWidth(it).toInt(),
                height = CGImageGetHeight(it).toInt(),
                mimeType = fallbackMimeType
            )
        }
    } finally {
        CGImageRelease(image)
        imageSource?.let { CFRelease(it) }
        CFRelease(data)
    }
}
