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
package com.wire.kalium.cells.data.model

import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.cells.domain.model.imagePreviewUrl
import com.wire.kalium.cells.domain.model.pdfPreviewUrl
import com.wire.kalium.cells.domain.model.toFileModel
import com.wire.kalium.cells.sdk.kmp.model.RestNode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CellNodePreviewTest {
    private fun decode(previews: String?) = Json.decodeFromString<RestNode>(
        """{"Path":"document.docx","Uuid":"asset"${previews?.let { ",\"Previews\":$it" } ?: ""}}"""
    ).toDto().toModel()

    @Test
    fun mixedRenditionsRetainTheirTypesAndMapToSeparateFileUrls() {
        val node = decode("""[
            {"ContentType":"application/pdf","Dimension":9000,"PreSignedGET":{"Url":"pdf"}},
            {"ContentType":"image/png","Dimension":400,"PreSignedGET":{"Url":"small"}},
            {"ContentType":"image/jpeg","Dimension":800,"PreSignedGET":{"Url":"large"}},
            {"PreSignedGET":{"Url":"unknown"}}
        ]""")
        assertEquals(listOf("pdf", "small", "large", "unknown"), node.previews?.map { it.url })
        assertEquals(0, node.previews?.last()?.dimension)
        assertEquals("large", node.toFileModel().previewUrl)
        assertEquals("pdf", node.toFileModel().pdfPreviewUrl)
    }

    @Test
    fun missingEmptyAndProcessingRenditionsAreNotReady() {
        listOf(null, "[]", """[{"Processing":true}]""",
            """[{"Error":true,"Processing":true}]""",
            """[{"PreSignedGET":{"Url":""}}]""").forEach { assertNull(decode(it).previews) }
    }

    @Test
    fun allTerminalErrorsAreAnEmptyListAndFailedUrlsAreDiscarded() {
        assertEquals(emptyList(), decode("""[{"Error":true,"PreSignedGET":{"Url":"failed"}}]""").previews)
        val node = decode("""[
            {"Error":true,"ContentType":"image/png","PreSignedGET":{"Url":"failed"}},
            {"ContentType":"application/pdf","PreSignedGET":{"Url":"pdf"}},
            {"ContentType":"image/png","Processing":true}
        ]""")
        assertEquals(listOf(NodePreview("pdf", 0, "application/pdf")), node.previews)
    }

    @Test
    fun selectorsHandleAbsentUnknownAndParameterizedContentTypes() {
        assertNull(null.imagePreviewUrl())
        assertNull(emptyList<NodePreview>().pdfPreviewUrl())
        assertNull(listOf(NodePreview("unknown", 500)).imagePreviewUrl())
        val previews = listOf(NodePreview("pdf", 0, "application/pdf; charset=binary"), NodePreview("image", 100, "image/png"))
        assertEquals("pdf", previews.pdfPreviewUrl())
        assertEquals("image", previews.imagePreviewUrl())
    }
}
