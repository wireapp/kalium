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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.mokkery.every
import dev.mokkery.matcher.matching
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertIs

class SendTextMessageCaseTest {

    @Test
    fun givenAValidMessage_whenSendingSomeText_thenShouldReturnASuccessResult() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text")

        // Then
        result.toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertyRepository.getReadReceiptsStatus()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(matching { message -> message.content is MessageContent.Text })
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matching { message -> message.content is MessageContent.Text },
                any()
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenNoNetwork_whenSendingSomeText_thenShouldReturnAFailure() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withSendMessageFailure()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .arrange()

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text")

        // Then
        result.toEither().shouldFail()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertyRepository.getReadReceiptsStatus()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenAMessageWithLinkPreview_whenSendingWithoutLinkPreviewImage_thenShouldSucceed() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()
        val linkPreviews = listOf(
            MessageLinkPreview(
                url = "",
                urlOffset = 0,
                permanentUrl = "",
                summary = "",
                title = "",
                image = null
            )
        )

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text", linkPreviews)

        // Then
        result.toEither().shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(matching { message ->
                (message.content as MessageContent.Text).linkPreviews.get(0).image == null
            })
        }
    }

    @Test
    fun givenAMessageWithLinkPreview_whenSendingWithLinkPreviewImage_thenShouldSucceedWithImage() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withUploadAndPersistPrivateAssetSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()
        val linkPreviews = listOf(
            MessageLinkPreview(
                url = "",
                urlOffset = 0,
                permanentUrl = "",
                summary = "",
                title = "",
                image = VALID_LINK_PREVIEW_ASSET
            )
        )

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text", linkPreviews)

        // Then
        result.toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matching { message ->
                    (message.content as MessageContent.Text).linkPreviews[0].image != null
                            && !(message.content as MessageContent.Text).linkPreviews[0].image?.otrKey.contentEquals(ByteArray(0))
                }
            )
        }
    }

    @Test
    fun givenAMessageWithLinkPreview_whenSendingWithWrongLinkPreviewImageData_thenShouldSucceedWithoutImage() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withUploadAndPersistPrivateAssetSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()
        val linkPreviews = listOf(
            MessageLinkPreview(
                url = "",
                urlOffset = 0,
                permanentUrl = "",
                summary = "",
                title = "",
                image = INVALID_LINK_PREVIEW_ASSET
            )
        )

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text", linkPreviews)

        // Then
        result.toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(matching { message ->
                (message.content as MessageContent.Text).linkPreviews.get(0).image != null
            })
        }
    }

    @Test
    fun givenAMessageWithLinkPreview_whenUploadingLinkPreviewImageDataFailed_thenShouldSucceedWithoutImage() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withUploadAndPersistPrivateAssetFailure()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()
        val linkPreviews = listOf(
            MessageLinkPreview(
                url = "",
                urlOffset = 0,
                permanentUrl = "",
                summary = "",
                title = "",
                image = VALID_LINK_PREVIEW_ASSET
            )
        )

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text", linkPreviews)

        // Then
        result.toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(matching { message ->
                assertIs<MessageContent.Text>(message.content)
                (message.content as MessageContent.Text).linkPreviews.get(0).image == null
            })
        }
    }

    @Test
    fun givenAMessageWithLinkPreview_whenUploadingLinkPreviewImage_thenShouldPassCorrectMetadata() = runTest {
        // Given
        val testUrl = "https://example.com"
        val testConversationId = TestConversation.ID
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withUploadAndPersistPrivateAssetSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()
        val linkPreviews = listOf(
            MessageLinkPreview(
                url = testUrl,
                urlOffset = 0,
                permanentUrl = testUrl,
                summary = "",
                title = "",
                image = VALID_LINK_PREVIEW_ASSET
            )
        )

        // When
        sendTextMessage(testConversationId, "some-text", linkPreviews)

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(
                mimeType = VALID_LINK_PREVIEW_ASSET.mimeType,
                assetDataPath = any(),
                otrKey = any(),
                extension = null,
                conversationId = testConversationId.toApi(),
                filename = "link-preview-$testUrl",
                filetype = VALID_LINK_PREVIEW_ASSET.mimeType
            )
        }
    }

    private class Arrangement(private val coroutineScope: CoroutineScope) {
        val persistMessage = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val assetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)
        val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val userPropertyRepository = mock<UserPropertyRepository>(mode = MockMode.autoUnit)
        val messageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)
        val observeSelfDeletionTimerSettingsForConversation = mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>(mode = MockMode.autoUnit)

        suspend fun withSendMessageSuccess() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withSendMessageFailure() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        }

        suspend fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(clientId)
        }

        suspend fun withPersistMessageSuccess() = apply {
            everySuspend {
                persistMessage.invoke(any())
            } returns Either.Right(Unit)
        }

        suspend fun withUploadAndPersistPrivateAssetSuccess() = apply {
            everySuspend {
                assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
            } returns Either.Right(Pair(UploadedAssetId("", "", ""), SHA256Key(ByteArray(0))))
        }

        suspend fun withUploadAndPersistPrivateAssetFailure() = apply {
            everySuspend {
                assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
            } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        }

        fun withSlowSyncStatusComplete() = apply {
            val stateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
            every {
                slowSyncRepository.slowSyncStatus
            } returns stateFlow
        }

        suspend fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            everySuspend {
                userPropertyRepository.getReadReceiptsStatus()
            } returns enabled
        }

        suspend fun withMessageTimer(result: SelfDeletionTimer) = apply {
            everySuspend {
                observeSelfDeletionTimerSettingsForConversation.invoke(any(), any())
            } returns flowOf(result)
        }

        fun arrange() = this to SendTextMessageUseCase(
            persistMessage,
            TestUser.SELF.id,
            currentClientIdProvider,
            assetRepository,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler,
            userPropertyRepository,
            observeSelfDeletionTimerSettingsForConversation,
            scope = coroutineScope,
            dispatchers = coroutineScope.testKaliumDispatcher
        )
    }

    private companion object {
        val VALID_LINK_PREVIEW_ASSET = LinkPreviewAsset(
            assetKey = "",
            assetDomain = "",
            assetToken = "",
            assetHeight = 80,
            assetWidth = 80,
            assetName = "",
            assetDataPath = "".toPath(),
            assetDataSize = 120,
            mimeType = "image/png"
        )
        val INVALID_LINK_PREVIEW_ASSET = LinkPreviewAsset(
            assetKey = "",
            assetDomain = "",
            assetToken = "",
            assetHeight = 0,
            assetWidth = 0,
            assetName = null,
            assetDataPath = "".toPath(),
            assetDataSize = 0,
            mimeType = ""
        )
    }

}
