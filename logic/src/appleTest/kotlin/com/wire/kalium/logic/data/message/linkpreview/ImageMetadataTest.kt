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

import com.wire.kalium.util.string.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageMetadataTest {

    @Test
    fun givenAnImage_whenReadingItRepeatedly_thenEachReadReturnsItsSize() {
        // Every read releases its Core Foundation objects; a double release would crash a later read.
        repeat(100) {
            assertEquals(ImageMetadata(width = 3, height = 2, mimeType = "image/png"), readImageMetadata(PNG_3_BY_2, "image/png"))
        }
    }

    @Test
    fun givenBytesThatAreNotAnImage_whenReading_thenNoMetadataIsReturned() {
        repeat(100) {
            assertNull(readImageMetadata("not an image".encodeToByteArray(), "image/png"))
            assertNull(readImageMetadata(PNG_3_BY_2.copyOf(16), "image/png"))
        }
        assertNull(readImageMetadata(ByteArray(0), "image/png"))
    }

    private companion object {
        val PNG_3_BY_2 = (
            "89504e470d0a1a0a0000000d49484452000000030000000208020000001216f14d0000001049444154789c63f8cfc000410c" +
                "70160041d205fb87f0b9480000000049454e44ae426082"
            ).hexToByteArray()
    }
}
