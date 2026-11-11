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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.max

internal actual fun readImageMetadata(bytes: ByteArray, fallbackMimeType: String): ImageMetadata? =
    withImageReader(bytes) { it.metadata(fallbackMimeType) }

internal actual fun preparePreviewImage(bytes: ByteArray, fallbackMimeType: String): PreviewImage? = withImageReader(bytes) { reader ->
    val metadata = reader.metadata(fallbackMimeType) ?: return@withImageReader null
    val sampleSize = (max(metadata.width, metadata.height) + MAX_PREVIEW_THUMBNAIL_DIMENSION - 1) /
        MAX_PREVIEW_THUMBNAIL_DIMENSION
    val parameters = reader.defaultReadParam.apply { setSourceSubsampling(sampleSize, sampleSize, 0, 0) }
    val image = reader.read(0, parameters) ?: return@withImageReader null
    try {
        val output = ByteArrayOutputStream()
        if (!ImageIO.write(image, "png", output)) return@withImageReader null
        PreviewImage(output.toByteArray(), ImageMetadata(image.width, image.height, "image/png"))
    } finally {
        image.flush()
    }
}

private fun ImageReader.metadata(mimeType: String): ImageMetadata? =
    ImageMetadata(getWidth(0), getHeight(0), mimeType).takeIf { it.hasSafeDimensions() }

private fun <T> withImageReader(bytes: ByteArray, block: (ImageReader) -> T?): T? = try {
    MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) return@use null
        val reader = readers.next()
        try {
            reader.setInput(input, true, true)
            block(reader)
        } finally {
            reader.dispose()
        }
    }
} catch (_: IOException) {
    null
}
