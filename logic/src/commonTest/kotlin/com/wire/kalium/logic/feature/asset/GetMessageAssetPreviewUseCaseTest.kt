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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.exceptions.CryptographyException
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class GetMessageAssetPreviewUseCaseTest {
    private val remote = TestMessage.ASSET_CONTENT.value.remoteData.copy(
        assetId = "preview-id", assetDomain = "preview.domain", otrKey = ByteArray(32) { 1 }, sha256 = ByteArray(32) { 2 },
    )
    private val preview = AssetContent.Preview("image/jpeg", 123, 64, 48, remote)
    private val content = TestMessage.ASSET_CONTENT.value.copy(preview = preview)
    private val message = TestMessage.assetMessage().copy(content = MessageContent.Asset(content))

    @Test
    fun downloadsOnlyPreviewWithItsOwnKeys() = runTest {
        val assets = mock<AssetRepository>()
        val messages = mock<MessageRepository>()
        everySuspend { messages.getMessageById(message.conversationId, message.id) } returns Either.Right(message)
        everySuspend {
            assets.fetchPreviewAsset(preview)
        } returns Either.Right(FetchedAssetData("preview.jpg".toPath(), true))
        val useCase = GetMessageAssetPreviewUseCaseImpl(
            assets, messages, this, StandardTestDispatcher(testScheduler).testKaliumDispatcher()
        )

        val result = assertIs<MessageAssetResult.Success>(useCase(message.conversationId, message.id).await())

        assertEquals("preview.jpg".toPath(), result.decodedAssetPath)
        assertEquals(123L, result.assetSize)
        verifySuspend(VerifyMode.exactly(1)) {
            assets.fetchPreviewAsset(preview)
        }
        verifySuspend(VerifyMode.exactly(0)) { assets.fetchDecodedAsset(content.remoteData.assetId) }
    }

    @Test
    fun decryptionExceptionReturnsFailure() = runTest {
        val assets = mock<AssetRepository>()
        val messages = mock<MessageRepository>()
        everySuspend { messages.getMessageById(message.conversationId, message.id) } returns Either.Right(message)
        everySuspend {
            assets.fetchPreviewAsset(preview)
        } throws CryptographyException("Invalid ciphertext")
        val useCase = GetMessageAssetPreviewUseCaseImpl(
            assets, messages, this, StandardTestDispatcher(testScheduler).testKaliumDispatcher()
        )
        val result = assertIs<MessageAssetResult.Failure>(useCase(message.conversationId, message.id).await())
        assertIs<StorageFailure.Generic>(result.coreFailure)
    }

    @Test
    fun previewDownloadCancellationStillPropagates() = runTest {
        val assets = mock<AssetRepository>()
        val messages = mock<MessageRepository>()
        everySuspend { messages.getMessageById(message.conversationId, message.id) } returns Either.Right(message)
        everySuspend {
            assets.fetchPreviewAsset(preview)
        } throws CancellationException("Session released")
        val useCase = GetMessageAssetPreviewUseCaseImpl(
            assets, messages, this, StandardTestDispatcher(testScheduler).testKaliumDispatcher()
        )
        assertFailsWith<CancellationException> { useCase(message.conversationId, message.id).await() }
    }

    @Test
    fun missingOrReplacedPreviewNeverFetchesOriginal() = runTest {
        val assets = mock<AssetRepository>()
        val messages = mock<MessageRepository>()
        everySuspend { messages.getMessageById(message.conversationId, message.id) } returns Either.Right(message)
        val useCase = GetMessageAssetPreviewUseCaseImpl(
            assets, messages, this, StandardTestDispatcher(testScheduler).testKaliumDispatcher()
        )
        assertIs<MessageAssetResult.Failure>(useCase(message.conversationId, message.id, AssetId("stale", "domain")).await())
        everySuspend { messages.getMessageById(message.conversationId, message.id) } returns
            Either.Right(message.copy(content = MessageContent.Asset(content.copy(preview = null))))
        assertIs<MessageAssetResult.Failure>(useCase(message.conversationId, message.id).await())
        verifySuspend(VerifyMode.exactly(0)) { assets.fetchPreviewAsset(any()) }
    }

    @Test
    fun oversizedMetadataIsRejectedBeforeRepositoryLookup() = runTest {
        val assets = mock<AssetRepository>()
        val messages = mock<MessageRepository>()
        everySuspend { messages.getMessageById(message.conversationId, message.id) } returns Either.Right(
            message.copy(content = MessageContent.Asset(content.copy(
                preview = preview.copy(sizeInBytes = AssetContent.Preview.MAX_SIZE_IN_BYTES + 1)
            )))
        )
        val useCase = GetMessageAssetPreviewUseCaseImpl(
            assets, messages, this, StandardTestDispatcher(testScheduler).testKaliumDispatcher()
        )

        assertIs<MessageAssetResult.Failure>(useCase(message.conversationId, message.id).await())

        verifySuspend(VerifyMode.exactly(0)) { assets.fetchPreviewAsset(any()) }
    }

    @Test
    fun pendingPreviewIsResolvedFromManagedLocalCache() = runTest {
        val assets = mock<AssetRepository>()
        val messages = mock<MessageRepository>()
        val pending = content.copy(preview = preview.copy(remoteData = remote.copy(sha256 = byteArrayOf())))
        everySuspend { messages.getMessageById(message.conversationId, message.id) } returns
            Either.Right(message.copy(content = MessageContent.Asset(pending)))
        everySuspend { assets.fetchPreviewAsset(pending.preview!!) } returns Either.Right(FetchedAssetData("local.jpg".toPath(), false))
        val useCase = GetMessageAssetPreviewUseCaseImpl(
            assets, messages, this, StandardTestDispatcher(testScheduler).testKaliumDispatcher()
        )
        val result = assertIs<MessageAssetResult.Success>(useCase(message.conversationId, message.id).await())
        assertEquals("local.jpg".toPath(), result.decodedAssetPath)
        verifySuspend(VerifyMode.exactly(1)) {
            assets.fetchPreviewAsset(pending.preview!!)
        }
    }
}
