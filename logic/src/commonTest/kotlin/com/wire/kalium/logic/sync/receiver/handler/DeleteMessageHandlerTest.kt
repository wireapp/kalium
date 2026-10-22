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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import kotlinx.coroutines.flow.flowOf
import okio.buffer
import okio.use
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.common.error.StorageFailure
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class DeleteMessageHandlerTest {

    @Test
    fun givenDeleteNotFromTheOriginalSender_whenOriginalMessageIsEpheral_thenDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = SELF_USER_ID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = Message.ExpirationData(expireAfter = Duration.parse("PT1H"))
            )

        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withDeleteMessage()
        }

        handler(
            content = content,
            senderUserId = UserId("requesterID", "requesterDomain"),
            conversationId = conversationId
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageDAO.deleteMessage(eq(originalMessageID), eq(conversationId.toDao()))
        }
    }

    @Test
    fun givenRequesterIsNotOriginalMessageSender_whenReceivingDeleteSignal_thenMarkAsDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val deleteMessageSenderID = UserId("deleteMessageSenderID", "deleteMessageSenderDomain")
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = deleteMessageSenderID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = null
            )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
        }

        handler(
            content = content,
            senderUserId = UserId("requesterID", "requesterDomain"),
            conversationId = conversationId
        )

        verifySuspend(VerifyMode.not) {
            arrangement.messageDAO.markMessageAsDeleted(eq(originalMessageID), eq(conversationId.toDao()))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.messageDAO.deleteMessage(eq(originalMessageID), eq(conversationId.toDao()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getMessageById(eq(conversationId), eq(originalMessageID))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.notificationEventsManager.scheduleDeleteMessageNotification(any())
        }
    }

    @Test
    fun givenOriginalMessageIsNotSelfDelete_whenReceivingDeleteSignal_thenMarkAsDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val deleteMessageSenderID = UserId("deleteMessageSenderID", "deleteMessageSenderDomain")
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = deleteMessageSenderID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = null
            )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withMarkAsDeleted()
        }

        handler(
            content = content,
            senderUserId = deleteMessageSenderID,
            conversationId = conversationId
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageDAO.markMessageAsDeleted(eq(originalMessageID), eq(conversationId.toDao()))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.messageDAO.deleteMessage(eq(originalMessageID), eq(conversationId.toDao()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getMessageById(eq(conversationId), eq(originalMessageID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.notificationEventsManager.scheduleDeleteMessageNotification(eq(originalMessage))
        }
    }

    @Test
    fun givenOriginalMessageIsSelfDelete_whenReceivingDeleteSignal_thenDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val deleteMessageSenderID = UserId("deleteMessageSenderID", "deleteMessageSenderDomain")
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = deleteMessageSenderID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = Message.ExpirationData(Duration.parse("10s"))
            )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withDeleteMessage()
        }

        handler(content = content, senderUserId = deleteMessageSenderID, conversationId = conversationId)

        verifySuspend(VerifyMode.not) {
            arrangement.messageDAO.markMessageAsDeleted(eq(originalMessageID), eq(conversationId.toDao()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageDAO.deleteMessage(eq(originalMessageID), eq(conversationId.toDao()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getMessageById(eq(conversationId), eq(originalMessageID))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.notificationEventsManager.scheduleDeleteMessageNotification(any())
        }
    }

    @Test
    fun givenSelfIsTheOneSentTheDeleteEvent_whenReceivingDeleteSignal_thenDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val originalMessageSenderID = UserId("originalMessageSenderID", "originalMessageSenderDomain")

        val deleteMessageSenderID = SELF_USER_ID
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = originalMessageSenderID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = Message.ExpirationData(Duration.parse("10s"))
            )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withDeleteMessage()
        }

        handler(content = content, senderUserId = deleteMessageSenderID, conversationId = conversationId)

        verifySuspend(VerifyMode.not) {
            arrangement.messageDAO.markMessageAsDeleted(eq(originalMessageID), eq(conversationId.toDao()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageDAO.deleteMessage(eq(originalMessageID), eq(conversationId.toDao()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getMessageById(eq(conversationId), eq(originalMessageID))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.notificationEventsManager.scheduleDeleteMessageNotification(any())
        }
    }

    @Test
    fun givenDeletedMessageHasRemoteAsset_whenHandled_thenLocalAssetIsDeleted() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val originalMessage = TestMessage.TEXT_MESSAGE.copy(
            content = MessageContent.Asset(
                TestMessage.ASSET_CONTENT.value.copy(
                    preview = AssetContent.Preview(
                        "image/jpeg", 123, 64, 48, TestMessage.ASSET_CONTENT.value.remoteData.copy(assetId = "preview-id")
                    )
                )
            ),
            conversationId = conversationId,
            expirationData = null,
        )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withMarkAsDeleted()
            withDeleteAssetLocally(Either.Right(Unit))
        }

        handler(
            content = MessageContent.DeleteMessage(originalMessage.id),
            conversationId = conversationId,
            senderUserId = originalMessage.senderUserId,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.deleteAssetLocally(
                TestMessage.ASSET_CONTENT.value.remoteData.assetId, TestMessage.ASSET_CONTENT.value.remoteData.assetDomain
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.deleteAssetLocally("preview-id", TestMessage.ASSET_CONTENT.value.remoteData.assetDomain)
        }
    }

    @Test
    fun previewDeletionPreservesSameKeyFromAnotherDomain() = runTest {
        verifyPreviewDeletionDomain("local.domain", "remote.domain", shouldDelete = false)
    }

    @Test
    fun previewDeletionRemovesMatchingDomain() = runTest {
        verifyPreviewDeletionDomain("remote.domain", "remote.domain", shouldDelete = true)
    }

    @Test
    fun previewDeletionNormalizesMissingDomain() = runTest {
        verifyPreviewDeletionDomain("", null, shouldDelete = true)
    }

    private suspend fun verifyPreviewDeletionDomain(cacheDomain: String, previewDomain: String?, shouldDelete: Boolean) {
        val fileSystem = FakeKaliumFileSystem()
        val path = fileSystem.providePersistentAssetPath("cached-preview")
        fileSystem.sink(path).buffer().use { it.write(byteArrayOf(1)) }
        val assetDAO = mock<AssetDAO>(mode = MockMode.autoUnit)
        everySuspend { assetDAO.getAssetByKey(any()) } returns flowOf(null)
        everySuspend { assetDAO.getAssetByKey("shared-key") } returns flowOf(
            AssetEntity("shared-key", cacheDomain, path.toString(), 1, downloadedDate = null)
        )
        val repository = AssetDataSource(
            mock(), assetDAO, SELF_USER_ID, assetAuditLog = lazy { mock() }, kaliumFileSystem = fileSystem
        )
        val preview = AssetContent.Preview(
            "image/jpeg", 1, 64, 48,
            TestMessage.ASSET_CONTENT.value.remoteData.copy(
                assetId = "shared-key", assetDomain = previewDomain, otrKey = ByteArray(32), sha256 = ByteArray(32)
            )
        )
        val message = TestMessage.TEXT_MESSAGE.copy(
            content = MessageContent.Asset(TestMessage.ASSET_CONTENT.value.copy(preview = preview)),
            expirationData = null,
        )
        val (_, handler) = arrange {
            withGetMessageById(Either.Right(message))
            withAssetRepository(repository)
        }

        handler(MessageContent.DeleteMessage(message.id), message.conversationId, message.senderUserId)

        assertEquals(!shouldDelete, fileSystem.exists(path))
        verifySuspend(if (shouldDelete) VerifyMode.exactly(1) else VerifyMode.not) { assetDAO.deleteAsset("shared-key") }
    }

    @Test
    fun unauthorizedAssetDeletionKeepsFiles() = runTest {
        val message = TestMessage.TEXT_MESSAGE.copy(content = TestMessage.ASSET_CONTENT, expirationData = null)
        val (arrangement, handler) = arrange { withGetMessageById(Either.Right(message)) }
        handler(MessageContent.DeleteMessage(message.id), message.conversationId, UserId("unrelated", "example.com"))
        verifySuspend(VerifyMode.not) { arrangement.assetRepository.deleteAssetLocally(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.messageDAO.markMessageAsDeleted(any(), any()) }
    }

    @Test
    fun assetCleanupFailureDoesNotPreventAuthorizedDeletion() = runTest {
        val message = TestMessage.TEXT_MESSAGE.copy(content = TestMessage.ASSET_CONTENT, expirationData = null)
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(message))
            withDeleteAssetLocally(Either.Left(StorageFailure.Generic(IllegalStateException("file cleanup failed"))))
        }

        handler(MessageContent.DeleteMessage(message.id), message.conversationId, message.senderUserId)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageDAO.markMessageAsDeleted(message.id, message.conversationId.toDao()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.notificationEventsManager.scheduleDeleteMessageNotification(message) }
    }

    private companion object {
        val SELF_USER_ID = UserId("selfID", "selfDomain")
    }

    private fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) {
        val messageRepository = mock<MessageRepository>()
        val messageDAO = mock<MessageDAO>(mode = MockMode.autoUnit)
        val assetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)
        private var repositoryOverride: AssetRepository? = null
        val notificationEventsManager = mock<NotificationEventsManager>(mode = MockMode.autoUnit)

        fun arrange() = run {
            runBlocking {
                withDeleteAssetLocally(Either.Right(Unit))
                block()
            }
            this@Arrangement to DeleteMessageHandlerImpl(
                messageRepository = messageRepository,
                messageDAO = messageDAO,
                assetRepository = repositoryOverride ?: assetRepository,
                notificationEventsManager = notificationEventsManager,
                selfUserId = SELF_USER_ID
            )
        }

        fun withAssetRepository(repository: AssetRepository) = apply { repositoryOverride = repository }

        suspend fun withGetMessageById(result: Either<StorageFailure, Message>) = apply {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns result
        }

        suspend fun withDeleteMessage() = apply {
            everySuspend {
                messageDAO.deleteMessage(any(), any())
            } returns Unit
        }

        suspend fun withMarkAsDeleted() = apply {
            everySuspend {
                messageDAO.markMessageAsDeleted(any(), any())
            } returns Unit
        }

        suspend fun withDeleteAssetLocally(result: Either<com.wire.kalium.common.error.CoreFailure, Unit>) = apply {
            everySuspend {
                assetRepository.deleteAssetLocally(any(), any())
            } returns result
        }
    }
}
