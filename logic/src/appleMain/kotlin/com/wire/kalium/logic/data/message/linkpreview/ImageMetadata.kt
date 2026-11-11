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
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceShouldCache
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize

internal actual fun readImageMetadata(bytes: ByteArray, fallbackMimeType: String): ImageMetadata? =
    withImageSource(bytes) { source, _ -> source.metadata(fallbackMimeType) }

internal actual fun preparePreviewImage(bytes: ByteArray, fallbackMimeType: String): PreviewImage? =
    withImageSource(bytes) { source, options ->
        source.metadata(fallbackMimeType) ?: return@withImageSource null
        val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, options) ?: return@withImageSource null
        try {
            encodePreviewPng(image)
        } finally {
            CGImageRelease(image)
        }
    }

@Suppress("ReturnCount")
private fun CGImageSourceRef.metadata(mimeType: String): ImageMetadata? {
    val properties = CFBridgingRelease(CGImageSourceCopyPropertiesAtIndex(this, 0u, null)) as? Map<*, *> ?: return null
    val width = (properties[CFBridgingRelease(CFRetain(kCGImagePropertyPixelWidth))] as? Number)?.toLong() ?: return null
    val height = (properties[CFBridgingRelease(CFRetain(kCGImagePropertyPixelHeight))] as? Number)?.toLong() ?: return null
    // Validate before narrowing native dimensions to Int.
    if (width !in 1L..MAX_PREVIEW_IMAGE_DIMENSION || height !in 1L..MAX_PREVIEW_IMAGE_DIMENSION) return null
    return ImageMetadata(width.toInt(), height.toInt(), mimeType).takeIf { it.hasSafeDimensions() }
}

@Suppress("ReturnCount")
private fun encodePreviewPng(image: CGImageRef): PreviewImage? {
    val output = CFDataCreateMutable(null, 0) ?: return null
    val pngType = CFBridgingRetain("public.png")
    return try {
        val destination = CGImageDestinationCreateWithData(output, pngType?.reinterpret(), 1u, null) ?: return null
        try {
            CGImageDestinationAddImage(destination, image, null)
            if (!CGImageDestinationFinalize(destination)) return null
            val encoded = CFDataGetBytePtr(output)?.readBytes(CFDataGetLength(output).toInt()) ?: return null
            PreviewImage(encoded, ImageMetadata(CGImageGetWidth(image).toInt(), CGImageGetHeight(image).toInt(), "image/png"))
        } finally {
            CFRelease(destination)
        }
    } finally {
        CFBridgingRelease(pngType)
        CFRelease(output)
    }
}

private fun imageSourceOptions(): CFDictionaryRef? {
    val options = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        ?: return null
    val maxSize = CFBridgingRetain(MAX_PREVIEW_THUMBNAIL_DIMENSION)
    CFDictionarySetValue(options, kCGImageSourceShouldCache, kCFBooleanFalse)
    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
    CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxSize)
    CFBridgingRelease(maxSize)
    return options
}

@Suppress("ReturnCount")
private fun <T> withImageSource(bytes: ByteArray, block: (CGImageSourceRef, CFDictionaryRef) -> T?): T? {
    if (bytes.isEmpty()) return null
    // The caller owns Core Foundation "Create" results; Kotlin/Native does not release them.
    val data = bytes.usePinned {
        CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong())
    } ?: return null
    try {
        val options = imageSourceOptions() ?: return null
        try {
            val source = CGImageSourceCreateWithData(data, options) ?: return null
            try {
                return block(source, options)
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(options)
        }
    } finally {
        CFRelease(data)
    }
}
