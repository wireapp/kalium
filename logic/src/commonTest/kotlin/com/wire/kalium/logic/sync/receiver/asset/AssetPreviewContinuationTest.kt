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
package com.wire.kalium.logic.sync.receiver.asset

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.framework.TestMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssetPreviewContinuationTest {
    private val remote = TestMessage.ASSET_CONTENT.value.remoteData.copy(
        assetId = "video-id", otrKey = ByteArray(32) { 1 }, sha256 = ByteArray(32) { 2 },
    )
    private val preview = AssetContent.Preview("image/jpeg", 1024, 512, 288, remote.copy(assetId = "preview-id"))
    private val content = TestMessage.ASSET_CONTENT.value.copy(
        mimeType = "video/mp4", sizeInBytes = 100_000, remoteData = remote, preview = preview,
    )
    private val existing = TestMessage.assetMessage().copy(content = MessageContent.Asset(content))

    @Test
    fun legacyFollowUpRetainsExistingPreviewAndOriginalCredentials() {
        val incoming = existing.copy(content = MessageContent.Asset(content.copy(preview = null)))
        val result = prepareReceivedAssetMessage(incoming, existing, restrictWithoutPreview = true)
        assertEquals(content, (result?.content as MessageContent.Asset).value)
    }

    @Test
    fun previewOnlyFollowUpMergesWithoutReplacingOriginalCredentials() {
        val current = existing.copy(content = MessageContent.Asset(content.copy(preview = null)))
        val incoming = existing.copy(content = MessageContent.Asset(content.copy(remoteData = remote.copy(
            otrKey = byteArrayOf(), sha256 = byteArrayOf(),
        ))))
        val result = prepareReceivedAssetMessage(incoming, current, restrictWithoutPreview = false)
        assertEquals(content, (result?.content as MessageContent.Asset).value)
    }

    @Test
    fun differentClientCannotAttachPreviewToExistingMessage() {
        assertNull(prepareReceivedAssetMessage(existing.copy(senderClientId = ClientId("different-client")), existing, false))
    }
}
