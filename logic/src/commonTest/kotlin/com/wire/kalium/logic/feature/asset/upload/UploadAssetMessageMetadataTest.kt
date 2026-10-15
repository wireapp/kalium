/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UploadAssetMessageMetadataTest {
    @Test
    fun givenQuickTimeMovie_whenCreatingMessage_thenPreservesVideoMetadata() {
        val upload = UploadAssetMessageMetadata(
            conversationId = ConversationId("conversation", "example.com"),
            mimeType = "video/quicktime",
            assetId = UploadedAssetId("asset", "example.com"),
            assetDataPath = "/private/movie".toPath(),
            assetDataSize = 1024L,
            assetName = "movie.mov",
            assetWidth = 1080,
            assetHeight = 1920,
            otrKey = AES256Key(ByteArray(32)),
            sha256Key = SHA256Key(ByteArray(32)),
            audioLengthInMs = 2500L,
            audioNormalizedLoudness = null,
        )

        val content = upload.toAssetContent()

        assertEquals("video/quicktime", content.mimeType)
        val video = assertIs<AssetContent.AssetMetadata.Video>(content.metadata)
        assertEquals(1080, video.width)
        assertEquals(1920, video.height)
        assertEquals(2500L, video.durationMs)
    }
}
