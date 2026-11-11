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

// Bound source allocations independently of the compressed download size. Use Long arithmetic
// so malicious dimensions cannot overflow the pixel count and bypass this check.
internal const val MAX_PREVIEW_IMAGE_DIMENSION = 8192
internal const val MAX_PREVIEW_IMAGE_PIXELS = 4_000_000L
internal const val MAX_PREVIEW_THUMBNAIL_DIMENSION = 1024

internal fun ImageMetadata.hasSafeDimensions(): Boolean =
    width in 1..MAX_PREVIEW_IMAGE_DIMENSION && height in 1..MAX_PREVIEW_IMAGE_DIMENSION &&
        width.toLong() * height.toLong() <= MAX_PREVIEW_IMAGE_PIXELS

internal data class PreviewImage(val bytes: ByteArray, val metadata: ImageMetadata)

/** Validates source dimensions before decoding a bounded first-frame PNG thumbnail. */
internal expect fun preparePreviewImage(bytes: ByteArray, fallbackMimeType: String): PreviewImage?
