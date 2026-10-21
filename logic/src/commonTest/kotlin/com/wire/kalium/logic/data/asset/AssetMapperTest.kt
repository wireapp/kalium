/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.EncryptionAlgorithmMapper
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.LegalHoldStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AssetMapperTest {

    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun breakDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenAudioAssetContent_whenMappingToProtoAssetMessage_thenReturnCorrectProtoAudioMetadata() = runTest {
        // given
        val audioMetadata = AssetContent.AssetMetadata.Audio(
            durationMs = 4444,
            normalizedLoudness = null
        )
        val messageContent = MessageContent.Asset(
            value = AssetContent(
                sizeInBytes = 10000,
                name = "audio.m4a",
                mimeType = "audio/mp4",
                metadata = audioMetadata,
                remoteData = AssetContent.RemoteData(
                    otrKey = byteArrayOf(),
                    sha256 = byteArrayOf(),
                    assetId = "abcd-1234",
                    assetToken = "token",
                    assetDomain = "domain",
                    encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
                )
            )
        )
        val (_, mapper) = Arrangement(testDispatcher)
            .arrange()

        // when
        val result = mapper.fromAssetContentToProtoAssetMessage(
            messageContent = messageContent,
            expectsReadConfirmation = true,
            legalHoldStatus = LegalHoldStatus.DISABLED
        )

        // then
        assertEquals(
            audioMetadata.durationMs,
            (result.original?.metaData as Asset.Original.MetaData.Audio).value.durationInMillis
        )
        assertNull(
            (result.original?.metaData as Asset.Original.MetaData.Audio).value.normalizedLoudness
        )
    }

    private fun videoWithPreview(): AssetContent {
        val original = AssetContent.RemoteData(
            byteArrayOf(1), byteArrayOf(2), "video", "video-token", "video.domain", MessageEncryptionAlgorithm.AES_CBC,
        )
        val preview = AssetContent.Preview(
            "image/jpeg", 1024, 512, 288,
            original.copy(
                assetId = "preview", assetDomain = "preview.domain", assetToken = "preview-token",
                otrKey = ByteArray(32) { 3 }, sha256 = ByteArray(32) { 4 },
            ),
        )
        return AssetContent(100_000, "video.mp4", "video/mp4", AssetContent.AssetMetadata.Video(1920, 1080, 1000), original, preview)
    }

    @Test
    fun videoPreviewWireRoundTripPreservesIndependentRemoteData() {
        val mapper = AssetMapperImpl()
        val content = videoWithPreview()
        val proto = mapper.fromAssetContentToProtoAssetMessage(MessageContent.Asset(content), true, LegalHoldStatus.DISABLED)
        val decoded = mapper.fromProtoAssetMessageToAssetContent(proto)
        assertEquals(content, decoded)
        assertEquals("video", decoded.remoteData.assetId)
        assertEquals("preview", decoded.preview?.remoteData?.assetId)
    }

    @Test
    fun pendingPreviewPersistenceRoundTripRetainsLocalIdentityButDoesNotPublishInvalidRemotePreview() {
        val video = videoWithPreview()
        val preview = requireNotNull(video.preview)
        val content = video.copy(preview = preview.copy(remoteData = preview.remoteData.copy(sha256 = byteArrayOf())))
        val entity = RegularMessageMapper(UserId("self", "domain")).toMessageEntityContent(MessageContent.Asset(content))
        val restored = AssetMapperImpl().fromAssetEntityToAssetContent(
            entity as com.wire.kalium.persistence.dao.message.MessageEntityContent.Asset,
        )
        assertEquals(content, restored)
        val proto = AssetMapperImpl().fromAssetContentToProtoAssetMessage(MessageContent.Asset(content), false, LegalHoldStatus.DISABLED)
        assertNull(proto.preview)
    }

    @Test
    fun invalidPreviewKeyAndHashLengthsAreRejectedOnWireAndPersistence() {
        val mapper = AssetMapperImpl()
        val video = videoWithPreview()
        val preview = requireNotNull(video.preview)
        val malformed = listOf(0, 1, 31, 33).map { length ->
            preview.copy(remoteData = preview.remoteData.copy(otrKey = ByteArray(length)))
        } + listOf(1, 31, 33).map { length ->
            preview.copy(remoteData = preview.remoteData.copy(sha256 = ByteArray(length)))
        }
        for (invalid in malformed) {
            val content = video.copy(preview = invalid)
            val wire = mapper.fromAssetContentToProtoAssetMessage(MessageContent.Asset(content), false, LegalHoldStatus.DISABLED)
            assertNull(mapper.fromProtoAssetMessageToAssetContent(wire).preview)
            val entity = RegularMessageMapper(UserId("self", "domain")).toMessageEntityContent(MessageContent.Asset(content))
            val restored = mapper.fromAssetEntityToAssetContent(
                entity as com.wire.kalium.persistence.dao.message.MessageEntityContent.Asset,
            )
            assertNull(restored.preview)
            assertEquals(video.remoteData, restored.remoteData)
        }
    }

    @Test
    fun missingAndMalformedPreviewsDoNotChangeOriginal() {
        val mapper = AssetMapperImpl()
        val content = videoWithPreview()
        val wire = mapper.fromAssetContentToProtoAssetMessage(MessageContent.Asset(content), false, LegalHoldStatus.DISABLED)
        for (preview in listOf(null, wire.preview!!.copy(mimeType = "video/mp4"), wire.preview!!.copy(size = 2_000_000))) {
            val result = mapper.fromProtoAssetMessageToAssetContent(wire.copy(preview = preview))
            assertNull(result.preview)
            assertEquals(content.remoteData, result.remoteData)
        }
    }

    private class Arrangement(
        testDispatcher: TestDispatcher
    ) {
        val mapper = AssetMapperImpl(
            encryptionAlgorithmMapper = EncryptionAlgorithmMapper(),
            dispatcher = testDispatcher.testKaliumDispatcher()
        )

        fun arrange() = this to mapper
    }
}
