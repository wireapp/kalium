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
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData

@Suppress("ReturnCount")
internal actual fun readImageMetadata(bytes: ByteArray, fallbackMimeType: String): ImageMetadata? {
    if (bytes.isEmpty()) return null

    val data = bytes.usePinned {
        CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong())
    }
    val imageSource = CGImageSourceCreateWithData(data, null) ?: return null
    val image = CGImageSourceCreateImageAtIndex(imageSource, 0u, null) ?: return null

    return ImageMetadata(
        width = CGImageGetWidth(image).toInt(),
        height = CGImageGetHeight(image).toInt(),
        mimeType = fallbackMimeType
    )
}
