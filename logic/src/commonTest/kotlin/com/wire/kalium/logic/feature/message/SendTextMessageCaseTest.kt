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
import com.wire.kalium.common.error.StorageFailure
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
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
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
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.mokkery.every
import dev.mokkery.matcher.matches
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
            arrangement.persistMessage.invoke(matches { message -> message.content is MessageContent.Text })
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matches { message -> message.content is MessageContent.Text },
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
        val failedMessageId = kotlin.test.assertIs<MessageOperationResult.Failure>(result).messageId
        kotlin.test.assertNotNull(failedMessageId)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(matches { it.id == failedMessageId })
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertyRepository.getReadReceiptsStatus()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), true)
        }
    }

    @Test
    fun givenNoNetworkAndPendingMessagesDisabled_whenSendingSomeText_thenDoesNotScheduleResend() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withSendMessageFailure()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withPendingMessagesDisabled()
            .arrange()

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text")

        // Then
        result.toEither().shouldFail()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), false)
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
            arrangement.persistMessage.invoke(matches { message ->
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
                matches { message ->
                    (message.content as MessageContent.Text).linkPreviews[0].image != null
                            && !(message.content as MessageContent.Text).linkPreviews[0].image?.otrKey.contentEquals(ByteArray(0))
                            && (message.content as MessageContent.Text).linkPreviews[0].image?.assetDataPath == PERSISTED_LINK_PREVIEW_PATH
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
            arrangement.persistMessage.invoke(matches { message ->
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
            arrangement.persistMessage.invoke(matches { message ->
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

    @Test
    fun givenPersistenceFailure_whenSendingText_thenNoRetryIdentityIsReturned() = runTest {
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .arrange()
        everySuspend { arrangement.persistMessage.invoke(any()) } returns
            Either.Left(com.wire.kalium.common.error.StorageFailure.DataNotFound)

        val result = sendTextMessage(TestConversation.ID, "not persisted", draftRevision = 12L)

        verifySuspend(VerifyMode.not) { arrangement.messageDraftRepository.consumePlainText(any(), any()) }
        kotlin.test.assertNull(kotlin.test.assertIs<MessageOperationResult.Failure>(result).messageId)
        verifySuspend(VerifyMode.not) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenPersistedText_whenTheScreenCallerIsCancelled_thenDomainDeliveryContinues() = runTest {
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .arrange()
        val deliveryGate = CompletableDeferred<Unit>()
        val delivered = CompletableDeferred<Unit>()
        everySuspend { arrangement.messageSender.sendMessage(any(), any()) } calls {
            deliveryGate.await()
            delivered.complete(Unit)
            Either.Right(Unit)
        }
        val screenCaller = launch { sendTextMessage(TestConversation.ID, "durable", draftRevision = 12L) }
        runCurrent()
        verifySuspend(VerifyMode.exactly(1)) { arrangement.persistMessage.invoke(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageDraftRepository.consumePlainText(TestConversation.ID, 12L) }
        screenCaller.cancelAndJoin()
        deliveryGate.complete(Unit)
        runCurrent()
        kotlin.test.assertTrue(delivered.isCompleted)
    }

    @Test
    fun givenPendingDraft_whenCallerLeavesBeforePersistenceAndReenters_thenOnlyOneMessageIsPersisted() = runTest {
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()
        val syncGate = CompletableDeferred<Unit>()
        everySuspend { arrangement.currentClientIdProvider.invoke() } calls {
            syncGate.await()
            Either.Right(TestClient.CLIENT_ID)
        }
        val firstScreen = launch { sendTextMessage(TestConversation.ID, "A", draftRevision = 4L) }
        runCurrent()
        firstScreen.cancelAndJoin()
        val reopenedScreen = launch { sendTextMessage(TestConversation.ID, "A", draftRevision = 4L) }
        runCurrent()
        verifySuspend(VerifyMode.not) { arrangement.persistMessage.invoke(any()) }
        syncGate.complete(Unit)
        reopenedScreen.join()
        verifySuspend(VerifyMode.exactly(1)) { arrangement.persistMessage.invoke(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenAnAlreadyConsumedRevision_whenAStalePreparedSubmissionResumes_thenNoDuplicateIsPersisted() = runTest {
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()
        var consumed = false
        everySuspend { arrangement.messageDraftRepository.isPlainTextConsumed(any(), any()) } calls { consumed }
        everySuspend { arrangement.messageDraftRepository.consumePlainText(any(), any()) } calls { consumed = true }
        sendTextMessage(TestConversation.ID, "A", draftRevision = 4L)
        runCurrent()
        sendTextMessage(TestConversation.ID, "A", draftRevision = 4L)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.persistMessage.invoke(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenAMessageId_whenStoringText_thenItIsStoredUnderThatIdBeforeTheSendEnds() = runTest {
        val sent = CompletableDeferred<Unit>()
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .arrange()
        everySuspend { arrangement.messageSender.sendMessage(any(), any()) } calls {
            sent.await()
            Either.Right(Unit)
        }

        kotlin.test.assertTrue(sendTextMessage.store("shared-id", TestConversation.ID, "some-text"))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(matches { it.id == "shared-id" })
        }
        sent.complete(Unit)
        runCurrent()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(matches { it.id == "shared-id" }, any())
        }
    }

    @Test
    fun givenStoringIsCancelledBeforeTheMessageIsStored_whenSyncCompletesLater_thenNothingIsStoredOrSent() = runTest {
        val slowSync = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Pending)
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()
        every { arrangement.slowSyncRepository.slowSyncStatus } returns slowSync.asStateFlow()

        // The importer is cancelled, for example by going to the background, while the account catches up.
        val storing = launch { sendTextMessage.store("shared-id", TestConversation.ID, "some-text") }
        runCurrent()
        storing.cancelAndJoin()
        slowSync.value = SlowSyncStatus.Complete
        runCurrent()

        verifySuspend(VerifyMode.not) { arrangement.persistMessage.invoke(any()) }
        verifySuspend(VerifyMode.not) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenStorageFails_whenStoringText_thenNothingIsStoredOrSent() = runTest {
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .arrange()
        everySuspend { arrangement.persistMessage.invoke(any()) } returns Either.Left(StorageFailure.DataNotFound)

        kotlin.test.assertFalse(sendTextMessage.store("shared-id", TestConversation.ID, "some-text"))

        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    private class Arrangement(private val coroutineScope: CoroutineScope) {
        val messageDraftRepository = mock<MessageDraftRepository>(mode = MockMode.autoUnit)
        val persistMessage = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val assetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)
        val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val userPropertyRepository = mock<UserPropertyRepository>(mode = MockMode.autoUnit)
        val messageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)
        val observeSelfDeletionTimerSettingsForConversation = mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>(mode = MockMode.autoUnit)
        private var pendingMessages = true

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
            everySuspend {
                assetRepository.fetchDecodedAsset("")
            } returns Either.Right(PERSISTED_LINK_PREVIEW_PATH)
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

        fun withPendingMessagesDisabled() = apply {
            pendingMessages = false
        }

        suspend fun arrange(): Pair<Arrangement, SendTextMessageUseCase> {
            everySuspend { messageDraftRepository.isPlainTextConsumed(any(), any()) } returns false
            return this to SendTextMessageUseCase(
            persistMessage = persistMessage,
            messageDraftRepository = messageDraftRepository,
            selfUserId = TestUser.SELF.id,
            provideClientId = currentClientIdProvider,
            assetDataSource = assetRepository,
            slowSyncRepository = slowSyncRepository,
            messageSender = messageSender,
            messageSendFailureHandler = messageSendFailureHandler,
            userPropertyRepository = userPropertyRepository,
            selfDeleteTimer = observeSelfDeletionTimerSettingsForConversation,
            scope = coroutineScope,
            dispatchers = coroutineScope.testKaliumDispatcher,
            pendingMessagesEnabled = pendingMessages
            )
        }
    }

    private companion object {
        val PERSISTED_LINK_PREVIEW_PATH = "persisted/link-preview.png".toPath()
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
