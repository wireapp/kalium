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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

internal actual fun readImageMetadata(bytes: ByteArray, fallbackMimeType: String): ImageMetadata? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return ImageMetadata(
        width = options.outWidth,
        height = options.outHeight,
        mimeType = fallbackMimeType
    ).takeIf { it.hasSafeDimensions() }
}

@Suppress("ReturnCount")
internal actual fun preparePreviewImage(bytes: ByteArray, fallbackMimeType: String): PreviewImage? {
    val metadata = readImageMetadata(bytes, fallbackMimeType) ?: return null
    // BitmapFactory rounds inSampleSize down to a power of two.
    var sampleSize = 1
    while ((max(metadata.width, metadata.height) + sampleSize - 1) / sampleSize > MAX_PREVIEW_THUMBNAIL_DIMENSION) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return try {
        val output = ByteArrayOutputStream()
        if (image.compress(Bitmap.CompressFormat.PNG, PNG_COMPRESSION_QUALITY, output)) {
            PreviewImage(output.toByteArray(), ImageMetadata(image.width, image.height, "image/png"))
        } else null
    } finally {
        image.recycle()
    }
}

private const val PNG_COMPRESSION_QUALITY = 100
