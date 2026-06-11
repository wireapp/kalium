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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenGraphScannerSanitizationTest {

    @Test
    fun givenControlCharactersAndWhitespace_whenScanning_thenSanitizesExtractedFields() {
        val dirtyTitle = "  Title${'\u0000'} with   spaces  "
        val dirtyDescription = "Line 1\n\nLine 2${'\u0007'}"

        val result = OpenGraphScanner.scan(
            htmlHead = """
                <head>
                    <meta property="og:title" content="$dirtyTitle" />
                    <meta property="og:description" content="$dirtyDescription" />
                    <meta property="og:url" content="https://example.com/page" />
                </head>
            """.trimIndent(),
            originalUrl = "https://example.com/page"
        )

        assertNotNull(result)
        assertEquals("Title with spaces", result.title)
        assertEquals("Line 1 Line 2", result.description)
    }

    @Test
    fun givenOverlongFields_whenScanning_thenTruncatesToConfiguredLimits() {
        val longTitle = "t".repeat(MAX_PREVIEW_TITLE_LENGTH + 25)
        val longDescription = "d".repeat(MAX_PREVIEW_DESCRIPTION_LENGTH + 50)

        val result = OpenGraphScanner.scan(
            htmlHead = """
                <head>
                    <meta property="og:title" content="$longTitle" />
                    <meta property="og:description" content="$longDescription" />
                    <meta property="og:url" content="https://example.com/page" />
                </head>
            """.trimIndent(),
            originalUrl = "https://example.com/page"
        )

        assertNotNull(result)
        assertEquals(MAX_PREVIEW_TITLE_LENGTH, result.title?.length)
        assertEquals(MAX_PREVIEW_DESCRIPTION_LENGTH, result.description?.length)
    }

    @Test
    fun givenWhitespaceOnlyImageUrl_whenScanning_thenSkipsImage() {
        val result = OpenGraphScanner.scan(
            htmlHead = """
                <head>
                    <meta property="og:title" content="Title" />
                    <meta property="og:url" content="https://example.com/page" />
                    <meta property="og:image" content="   " />
                </head>
            """.trimIndent(),
            originalUrl = "https://example.com/page"
        )

        assertNotNull(result)
        assertEquals(emptyList(), result.imageUrls)
    }
}
