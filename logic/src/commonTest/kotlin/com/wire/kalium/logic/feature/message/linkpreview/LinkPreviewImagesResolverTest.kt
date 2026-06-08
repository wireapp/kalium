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
package com.wire.kalium.logic.feature.message.linkpreview

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.MockMode
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import kotlin.test.Test

class LinkPreviewImagesResolverTest {

    @Test
    fun givenMessageWithRemotePreviewImage_whenInvoked_thenShouldResolveAndPersistLocalPath() = runTest {
        val resolvedPath = "/tmp/link-preview.png".toPath()
        val (arrangement, useCase) = Arrangement(this)
            .withMessage(TEST_MESSAGE)
            .withResolvedAsset(resolvedPath)
            .arrange()

        useCase(TestConversation.ID, TEST_MESSAGE.id)
        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.fetchPrivateDecodedAsset(
                assetId = "asset-key",
                assetDomain = "wire.com",
                assetName = "preview.png",
                mimeType = "image/png",
                assetToken = "asset-token",
                encryptionKey = any(),
                assetSHA256Key = any(),
                downloadIfNeeded = true
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateLinkPreviewImageLocalPath(
                conversationId = TestConversation.ID,
                messageId = TEST_MESSAGE.id,
                urlOffset = 6,
                localPath = resolvedPath.toString()
            )
        }
    }

    @Test
    fun givenMessageWithLocalPreviewImage_whenInvoked_thenShouldNotDownloadAgain() = runTest {
        val message = TEST_MESSAGE.copy(
            linkPreviews = listOf(
                TEST_PREVIEW.copy(
                    image = TEST_IMAGE.copy(assetDataPath = "/tmp/already-there.png".toPath())
                )
            )
        )
        val (arrangement, useCase) = Arrangement(this)
            .withMessage(message)
            .arrange()

        useCase(TestConversation.ID, TEST_MESSAGE.id)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.assetRepository.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.updateLinkPreviewImageLocalPath(any(), any(), any(), any())
        }
    }

    @Test
    fun givenPreviewImageResolutionFails_whenInvoked_thenShouldNotPersistLocalPath() = runTest {
        val (arrangement, useCase) = Arrangement(this)
            .withMessage(TEST_MESSAGE)
            .withAssetFailure()
            .arrange()

        useCase(TestConversation.ID, TEST_MESSAGE.id)
        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.updateLinkPreviewImageLocalPath(any(), any(), any(), any())
        }
    }

    @Test
    fun givenLinkPreviewDisabled_whenInvoked_thenDoesNothing() = runTest {
        val (arrangement, useCase) = Arrangement(this)
            .withMessage(TEST_MESSAGE)
            .arrangeDisabled()

        useCase(TestConversation.ID, TEST_MESSAGE.id)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.getMessageById(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.assetRepository.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.updateLinkPreviewImageLocalPath(any(), any(), any(), any())
        }
    }

    private class Arrangement(
        private val scope: CoroutineScope
    ) {
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val assetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)

        init {
            everySuspend {
                messageRepository.updateLinkPreviewImageLocalPath(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        fun withMessage(message: Message) = apply {
            everySuspend { messageRepository.getMessageById(TestConversation.ID, TEST_MESSAGE.id) } returns Either.Right(message)
        }

        fun withResolvedAsset(path: okio.Path) = apply {
            everySuspend {
                assetRepository.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Either.Right(FetchedAssetData(path, true))
        }

        fun withAssetFailure() = apply {
            everySuspend {
                assetRepository.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        }

        fun arrange() = this to LinkPreviewImagesResolverImpl(
            messageRepository = messageRepository,
            assetRepository = assetRepository,
            scope = scope,
            dispatcher = scope.testKaliumDispatcher,
            linkPreviewEnabled = true
        )

        fun arrangeDisabled() = this to LinkPreviewImagesResolverImpl(
            messageRepository = messageRepository,
            assetRepository = assetRepository,
            scope = scope,
            dispatcher = scope.testKaliumDispatcher,
            linkPreviewEnabled = false
        )
    }
}

private val TEST_IMAGE = LinkPreviewAsset(
    mimeType = "image/png",
    assetDataPath = null,
    assetDataSize = 128,
    assetHeight = 630,
    assetWidth = 1200,
    assetName = "preview.png",
    assetKey = "asset-key",
    assetToken = "asset-token",
    assetDomain = "wire.com",
    otrKey = byteArrayOf(1, 2, 3),
    sha256Key = byteArrayOf(4, 5, 6),
    encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
)

private val TEST_PREVIEW = MessageLinkPreview(
    url = "https://example.com",
    urlOffset = 6,
    permanentUrl = "https://example.com/permalink",
    title = "Title",
    summary = "Summary",
    image = TEST_IMAGE
)

private val TEST_MESSAGE = Message.Regular(
    id = "message-id",
    content = MessageContent.Text(value = "Hello https://example.com", linkPreviews = listOf(TEST_PREVIEW)),
    conversationId = TestConversation.ID,
    date = Instant.fromEpochMilliseconds(0),
    senderUserId = TestUser.OTHER.id,
    status = Message.Status.Sent,
    isSelfMessage = false,
    senderClientId = ClientId("client-id"),
    editStatus = Message.EditStatus.NotEdited,
    linkPreviews = listOf(TEST_PREVIEW)
)
