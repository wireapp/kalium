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
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.LegalHoldStatus
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssetMapperTest {

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
        val (_, mapper) = Arrangement()
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

    private class Arrangement {

        @Mock
        val dispatcher = mock(KaliumDispatcher::class)

        val mapper = AssetMapperImpl(
            encryptionAlgorithmMapper = EncryptionAlgorithmMapper(),
            dispatcher = dispatcher
        )

        fun arrange() = this to mapper
    }
}
