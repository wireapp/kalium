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

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

open class PreviewImageTest {
    @Test
    fun givenExcessiveImageHeaders_whenReadingOrPreparing_thenRejectsThem() {
        listOf(PreviewImageTestData.PNG_6000_BY_6000, PreviewImageTestData.PNG_8193_BY_1).forEach { bytes ->
            assertNull(readImageMetadata(bytes, "image/png"))
            assertNull(preparePreviewImage(bytes, "image/png"))
        }
    }

    @Test
    fun givenImageWithinBudget_whenPreparing_thenReturnsBoundedPng() {
        val thumbnail = assertNotNull(preparePreviewImage(PreviewImageTestData.PNG_1600_BY_800, "image/png"))
        val metadata = assertNotNull(readImageMetadata(thumbnail.bytes, thumbnail.metadata.mimeType))
        assertEquals(thumbnail.metadata, metadata)
        assertEquals("image/png", metadata.mimeType)
        assertTrue(metadata.width in 1..MAX_PREVIEW_THUMBNAIL_DIMENSION)
        assertTrue(metadata.height in 1..MAX_PREVIEW_THUMBNAIL_DIMENSION)
        assertEquals(metadata.width, metadata.height * 2)
    }
}

internal object PreviewImageTestData {
    val PNG_1600_BY_800 = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAABkAAAAMgAQAAAABTu5P3AAAAs0lEQVR4nO3BMQEAAADCoPVPbQhfoAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgNcAdD4AAY1am6gA" +
        "AAAASUVORK5CYII="
    )

    // Oversized RGB header with a small body; only the JVM regression needs a full valid raster.
    val PNG_6000_BY_6000 = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAF3AAABdwCAIAAABsNhAU"
    ) + PNG_1600_BY_800.copyOfRange(33, PNG_1600_BY_800.size)

    val PNG_8193_BY_1 = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAIAEAAAABAQAAAACx8nbzAAAAEUlEQVR4nGNgGAWjYBSMXAAABAIAARSFVjUAAAAASUVORK5CYII="
    )
}
