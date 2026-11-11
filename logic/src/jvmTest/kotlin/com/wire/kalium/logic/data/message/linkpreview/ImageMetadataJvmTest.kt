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

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageMetadataJvmTest {
    @Test
    fun givenHighlyCompressed36MegapixelPng_whenReadingOrPreparing_thenRejectsBeforeDecoding() {
        // Write rows directly into the compressor, avoiding a full-size raster in the test itself.
        // This valid RGB PNG is about 105 KB but would need over 100 MiB to decode.
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { output ->
            val row = ByteArray(1 + 6000 * 3)
            repeat(6000) { output.write(row) }
        }
        val image = ByteArrayOutputStream().apply {
            write(PreviewImageTestData.PNG_6000_BY_6000.copyOf(33))
            writeChunk("IDAT", compressed.toByteArray())
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()

        assertTrue(image.size < 5 * 1024 * 1024)
        assertNull(readImageMetadata(image, "image/png"))
        assertNull(preparePreviewImage(image, "image/png"))
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, bytes: ByteArray) {
        val chunkType = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(chunkType)
            update(bytes)
        }
        DataOutputStream(this).apply {
            writeInt(bytes.size)
            write(chunkType)
            write(bytes)
            writeInt(crc.value.toInt())
        }
    }
}
