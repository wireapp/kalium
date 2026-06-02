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

import com.wire.kalium.persistence.dao.message.MessageEntity
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LinkPreviewMapperTest {

    @Test
    fun givenDaoLinkPreviewWithImagePath_whenMappingToModel_thenImagePathIsConvertedToOkioPath() {
        val linkPreview = MessageEntity.LinkPreview(
            url = "https://example.com",
            urlOffset = 4,
            permanentUrl = "https://example.com/permalink",
            title = "Title",
            summary = "Summary",
            imageLocalPath = "/tmp/link-preview.png",
            imageWidth = 1200,
            imageHeight = 630,
            imageMimeType = "image/png"
        )

        val result = LinkPreviewMapperImpl().fromDaoToModel(linkPreview)

        val image = assertNotNull(result.image)
        assertEquals("/tmp/link-preview.png".toPath(), image.assetDataPath)
        assertEquals(1200, image.assetWidth)
        assertEquals(630, image.assetHeight)
        assertEquals("image/png", image.mimeType)
    }
}
