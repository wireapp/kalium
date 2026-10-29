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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.answering.returns
import dev.mokkery.answering.calls
import dev.mokkery.MockMode
import com.wire.kalium.common.error.NetworkFailure
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoreSharedCellsMessageTest {
    @Test
    fun storesOnlySuppliedFilesWithStableIdentityAndLeavesTheComposerUntouched() = verifyStoredShare(false)

    @Test
    fun publicationFailureKeepsStoredAttachmentsAndDoesNotSend() = verifyStoredShare(true)

    private fun verifyStoredShare(publicationFails: Boolean) = runTest {
        val conversation = TestConversation.ID
        val conversations = mock<ConversationRepository>()
        val properties = mock<UserPropertyRepository>()
        val timer = mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>()
        val drafts = mock<MessageAttachmentDraftRepository>()
        val removeDrafts = mock<RemoveAttachmentDraftsUseCase>()
        val sender = mock<MessageSender>()
        val publisher = mock<com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase>()
        val stored = mutableListOf<Message.Regular>()
        everySuspend { publisher(any()) } calls {
            assertEquals(1, stored.size, "The message must own the drafts before publication")
            if (publicationFails) Either.Left(NetworkFailure.NoNetworkConnection(null)) else Either.Right(Unit)
        }
        everySuspend { conversations.isCellEnabled(conversation) } returns Either.Right(true)
        everySuspend { properties.getReadReceiptsStatus() } returns false
        everySuspend { timer(conversation, true) } returns flowOf(SelfDeletionTimer.Disabled)
        everySuspend { sender.sendMessage(any(), any()) } returns Either.Right(Unit)
        val useCase = SendMultipartMessageUseCase(
            persistMessage = PersistMessageUseCase { stored += it as Message.Regular; Either.Right(Unit) },
            selfUserId = TestUser.SELF.id,
            provideClientId = CurrentClientIdProvider { Either.Right(ClientId("client")) },
            assetDataSource = mock(),
            slowSyncRepository = mock(),
            messageSender = sender,
            messageSendFailureHandler = mock(mode = MockMode.autoUnit),
            userPropertyRepository = properties,
            conversationRepository = conversations,
            attachmentsRepository = drafts,
            selfDeleteTimer = timer,
            publishAttachments = publisher,
            removeAttachmentDrafts = removeDrafts,
            sendAssetMessage = mock(),
            dispatchers = StandardTestDispatcher(testScheduler).testKaliumDispatcher(),
            scope = backgroundScope,
        )
        val attachment = CellAssetContent(
            id = "shared-node", versionId = "version", mimeType = "text/plain", assetPath = "cell/a.txt",
            assetSize = 1, localPath = null, previewUrl = null, metadata = null, transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
        )
        assertTrue(useCase.storeShared("stable-id", conversation, "Caption", listOf(attachment)))
        runCurrent()
        assertEquals("stable-id", stored.single().id)
        assertEquals(MessageContent.Multipart("Caption", attachments = listOf(attachment)), stored.single().content)
        verifySuspend(VerifyMode.exactly(0)) { drafts.getAll(any()) }
        verifySuspend(VerifyMode.exactly(0)) { removeDrafts(any()) }
        verifySuspend(VerifyMode.exactly(1)) { publisher(listOf(attachment)) }
        verifySuspend(VerifyMode.exactly(if (publicationFails) 0 else 1)) { sender.sendMessage(stored.single(), any()) }
    }
}
