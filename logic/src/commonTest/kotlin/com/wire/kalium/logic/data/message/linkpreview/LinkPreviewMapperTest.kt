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

import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.EncryptionAlgorithm
import com.wire.kalium.protobuf.messages.LinkPreview
import com.wire.kalium.persistence.dao.message.MessageEntity
import okio.Path.Companion.toPath
import pbandk.ByteArr
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun givenProtoLinkPreviewWithRemoteImage_whenMappingThroughDao_thenRemoteImageMetadataIsPreserved() {
        val mapper = LinkPreviewMapperImpl()
        val proto = LinkPreview(
            url = "https://example.com",
            urlOffset = 4,
            permanentUrl = "https://example.com/permalink",
            title = "Title",
            summary = "Summary",
            image = Asset(
                original = Asset.Original(
                    mimeType = "image/png",
                    size = 128,
                    name = "preview.png",
                    metaData = Asset.Original.MetaData.Image(
                        Asset.ImageMetaData(
                            width = 1200,
                            height = 630
                        )
                    )
                ),
                status = Asset.Status.Uploaded(
                    Asset.RemoteData(
                        assetId = "asset-key",
                        assetToken = "asset-token",
                        assetDomain = "wire.com",
                        otrKey = ByteArr(byteArrayOf(1, 2, 3)),
                        sha256 = ByteArr(byteArrayOf(4, 5, 6)),
                        encryption = EncryptionAlgorithm.AES_GCM
                    )
                )
            )
        )

        val model = mapper.fromProtoToModel(proto)
        val dao = mapper.fromModelToDao(model)
        val result = mapper.fromDaoToModel(dao)

        val image = assertNotNull(result.image)
        assertEquals("asset-key", image.assetKey)
        assertEquals("asset-token", image.assetToken)
        assertEquals("wire.com", image.assetDomain)
        assertContentEquals(byteArrayOf(1, 2, 3), image.otrKey)
        assertContentEquals(byteArrayOf(4, 5, 6), image.sha256Key)
        assertEquals(MessageEncryptionAlgorithm.AES_GCM, image.encryptionAlgorithm)
        assertEquals("image/png", image.mimeType)
        assertEquals(1200, image.assetWidth)
        assertEquals(630, image.assetHeight)
    }

    @Test
    fun givenModelLinkPreviewWithRemoteImage_whenMappingToProto_thenRemoteImageMetadataIsPreserved() {
        val image = LinkPreviewAsset(
            mimeType = "image/png",
            assetDataPath = null,
            assetDataSize = 128,
            assetWidth = 1200,
            assetHeight = 630,
            assetKey = "asset-key",
            assetToken = "asset-token",
            assetDomain = "wire.com",
            otrKey = byteArrayOf(1, 2, 3),
            sha256Key = byteArrayOf(4, 5, 6),
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
        )
        val model = MessageLinkPreview(
            url = "https://example.com",
            urlOffset = 4,
            permanentUrl = "https://example.com/permalink",
            title = "Title",
            summary = "Summary",
            image = image
        )

        val result = LinkPreviewMapperImpl().fromModelToProto(model)

        assertEquals("asset-key", result.image?.uploaded?.assetId)
        assertEquals("asset-token", result.image?.uploaded?.assetToken)
        assertEquals("wire.com", result.image?.uploaded?.assetDomain)
        assertContentEquals(byteArrayOf(1, 2, 3), result.image?.uploaded?.otrKey?.array)
        assertContentEquals(byteArrayOf(4, 5, 6), result.image?.uploaded?.sha256?.array)
        assertEquals(EncryptionAlgorithm.AES_GCM, result.image?.uploaded?.encryption)
    }

    @Test
    fun givenModelLinkPreviewWithQuotedSummary_whenMappingToDao_thenSummaryIsEscapedForStorage() {
        val model = MessageLinkPreview(
            url = "https://example.com",
            urlOffset = 4,
            permanentUrl = "https://example.com/permalink",
            title = "Title \"quoted\"",
            summary = "Summary with \"quotes\" and \n line break",
            image = null
        )

        val result = LinkPreviewMapperImpl().fromModelToDao(model)

        assertTrue(result.title.contains("\\\""))
        assertTrue(result.summary.contains("\\\""))
        assertTrue(result.summary.contains("\\n"))
    }
}
