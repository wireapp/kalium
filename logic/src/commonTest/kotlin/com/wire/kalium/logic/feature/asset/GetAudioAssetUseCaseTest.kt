/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentUseCase
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCase
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertTrue

class GetAudioAssetUseCaseTest {

    @Test
    fun givenWireCellsEnabledAndAssetIdPresent_whenCellAssetContentReturned_thenReturnsSuccess() = runTest {
        // Given
        val (_, useCase) = Arrangement()
            .withCellsEnabled()
            .withMessageAttachmentResult()
            .arrange()

        // When
        val result = useCase.invoke(conversationId, "messageId", "assetId").await()

        // Then
        assertTrue(result is MessageAssetResult.Success)
    }

    @Test
    fun givenWireCellsEnabledAndAssetIdPresent_whenErrorReturned_thenReturnsFailure() = runTest {
        // Given
        val (_, useCase) = Arrangement()
            .withCellsEnabled()
            .withMessageAttachmentFailure()
            .arrange()

        // When
        val result = useCase.invoke(conversationId, "messageId", "assetId").await()

        // Then
        assertTrue(result is MessageAssetResult.Failure)
    }

    @Test
    fun givenWireCellsDisabled_whenInvoked_thenInvokeGetMessageAssetUseCaseOnce() = runTest {
        // Given
        // Given
        val (arrangement, useCase) = Arrangement()
            .withCellsDisabled()
            .withGetMessageAssetUseCaseReturning()
            .arrange()

        // When
        useCase.invoke(conversationId, "messageId", "assetId").await()

        // Then
        coVerify { arrangement.getMessageAsset(any(), any()) }.wasInvoked(once)
    }

    private class Arrangement {

        val isWireCellsEnabledForConversation = mock(IsWireCellsEnabledForConversationUseCase::class)
        val getMessageAsset = mock(GetMessageAssetUseCase::class)
        val getMessageAttachment = mock(GetMessageAttachmentUseCase::class)

        suspend fun withMessageAttachmentResult() = apply {
            coEvery { getMessageAttachment(any()) } returns Either.Right(
                AssetContent(
                    0L,
                    "name",
                    "image/jpg",
                    AssetContent.AssetMetadata.Image(100, 100),
                    AssetContent.RemoteData(
                        otrKey = ByteArray(0),
                        sha256 = ByteArray(16),
                        assetId = "asset-id",
                        assetToken = "==some-asset-token",
                        assetDomain = "some-asset-domain.com",
                        encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
                    ),
                    AssetContent.LocalData(
                        assetDataPath = "local_asset_path"
                    ),
                )
            )
        }

        suspend fun withMessageAttachmentFailure() = apply {
            coEvery { getMessageAttachment(any()) } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withCellsEnabled() = apply {
            coEvery { isWireCellsEnabledForConversation(any()) } returns true
        }

        suspend fun withCellsDisabled() = apply {
            coEvery { isWireCellsEnabledForConversation(conversationId) } returns false
        }

        suspend fun withGetMessageAssetUseCaseReturning() = apply {
            coEvery {
                getMessageAsset(conversationId, "messageId")
            } returns CompletableDeferred(MessageAssetResult.Success("path".toPath(), 123, "name"))
        }

        fun arrange() = this to GetAudioAssetUseCaseImpl(isWireCellsEnabledForConversation, getMessageAsset, getMessageAttachment)
    }

    companion object {
        val conversationId = ConversationId("value", "domain")
    }
}
