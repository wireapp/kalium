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

package com.wire.kalium.logic.data.message.timeline

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.toMessageContent
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.logic.feature.message.OpenMessageTimelineUseCase
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.PollOptionEntity
import com.wire.kalium.persistence.dao.message.RecipientFailureTypeEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MessageTimelineTest {
    private val dispatcher = StandardTestDispatcher()
    private val self = TestUser.ENTITY.copy(id = TestUser.ENTITY.id.copy(value = "timeline-self"))
    private val other = TestUser.ENTITY.copy(id = TestUser.ENTITY.id.copy(value = "timeline-other"), name = "Other sender")
    private val database = TestUserDatabase(self.id, dispatcher)
    private val db = database.builder
    private val conversation = MockConversation.entity().copy(type = ConversationEntity.Type.GROUP)
    private val sessions = mutableListOf<MessageTimeline>()
    private val sources = mutableListOf<MessageTimelinePagingSource>()

    @AfterTest
    fun tearDown() {
        sources.forEach { it.invalidate() }
        sessions.forEach { it.close() }
        database.delete()
    }

    private suspend fun seed(messages: List<MessageEntity>) {
        db.userDAO.upsertUsers(listOf(self, other))
        db.conversationDAO.insertConversations(listOf(conversation))
        db.messageDAO.insertOrIgnoreMessages(messages, withUnreadEvents = false)
    }

    private fun message(id: String, content: MessageEntityContent.Regular = MessageEntityContent.Text(id)) = TestMessage.ENTITY.copy(
        id = id, conversationId = conversation.id, senderUserId = other.id, content = content,
        date = Instant.fromEpochMilliseconds(1_000),
    )

    private val repository = MessageTimelineRepository(db.messageTimelineDAO, self.id.toModel())

    private fun open() = OpenMessageTimelineUseCase(repository)(conversation.id.toModel()).also(sessions::add)

    private suspend fun MessageTimeline.page(request: MessageTimelineRequest = MessageTimelineRequest.Latest, limit: Int = 60) =
        assertIs<MessageTimelineResult.Success>(load(request, limit)).page

    private fun source(initial: MessageTimelineRequest = MessageTimelineRequest.Latest) = repository.pagingSource(
        conversation.id.toModel(), MessageTimelinePolicy.DEFAULT, initial
    ).also(sources::add)

    @Test
    fun textQuotesMentionsAndPreviewsPreserveRenderingData() = runTest(dispatcher) {
        val body = "Long text with \"quotes\", slashes \\ and \n newlines. ".repeat(2_000)
        val preview = MessageEntity.LinkPreview(
            "https://wire.com/\"quoted", 5, "https://wire.com/canonical", "Title \"quoted\"", "A\nB", "/image.png", 80, 40,
            "image/png", imageAssetKey = "asset-key", imageAssetToken = "private-token", imageOtrKey = "private-key",
        )
        val text = MessageEntityContent.Text(
            body, linkPreview = listOf(preview.copy(urlOffset = 50), preview),
            mentions = listOf(MessageEntity.Mention(2, 4, self.id), MessageEntity.Mention(12, 3, other.id)),
            quotedMessageId = "original", isQuoteVerified = true,
        )
        seed(listOf(message("original", MessageEntityContent.Text("Original \"body\"")).copy(senderUserId = self.id), message("reply", text)))
        val item = open().page().items.first { it.id == "reply" }
        val rendered = assertIs<MessageListContent.Text>(item.content).text
        assertEquals(body, rendered.body)
        assertEquals(listOf(2, 12), rendered.mentions.map { it.start })
        assertEquals(listOf(true, false), rendered.mentions.map { it.isSelfMention })
        assertEquals(listOf(5, 50), rendered.linkPreviews.map { it.urlOffset })
        assertEquals(preview.title, rendered.linkPreviews.first().title)
        assertEquals(preview.summary, rendered.linkPreviews.first().summary)
        assertEquals("/image.png", rendered.linkPreviews.first().imageLocalPath)
        assertEquals(80, rendered.linkPreviews.first().imageWidth)
        assertEquals("original", rendered.quotedMessageId)
        assertEquals(MessageContent.QuotedMessageDetails.Text("Original \"body\""), rendered.quote?.quotedContent)
        assertTrue(assertNotNull(rendered.quote).isQuotingSelfUser)
        assertEquals(self.id.toModel(), rendered.quote?.senderId)
        assertEquals(other.name, item.sender.name)
        assertFalse(item.sender.isSelf)
    }

    @Test
    fun quoteChangesOutsideThePageInvalidateAndRefreshTheirPreview() = runTest(dispatcher) {
        seed(listOf(
            message("original", MessageEntityContent.Text("before")).copy(date = Instant.fromEpochMilliseconds(0)),
            message("reply", MessageEntityContent.Text("reply", quotedMessageId = "original", isQuoteVerified = true)),
        ))
        val timeline = open()
        val reply = timeline.page(limit = 1).items.single()
        db.messageDAO.updateTextMessageContent(conversation.id, "original", MessageEntityContent.Text("after"))
        assertTrue(timeline.invalidated.value)
        val updated = open().page(MessageTimelineRequest.AroundMessage(reply.id, reply.cursor), 1).items.single()
        assertEquals(MessageContent.QuotedMessageDetails.Text("after"), assertIs<MessageListContent.Text>(updated.content).text.quote?.quotedContent)
        db.messageDAO.markMessageAsDeleted("original", conversation.id)
        val deleted = open().page(limit = 1).items.single()
        assertEquals(MessageContent.QuotedMessageDetails.Deleted, assertIs<MessageListContent.Text>(deleted.content).text.quote?.quotedContent)
        db.messageDAO.deleteMessage("original", conversation.id)
        val missing = assertIs<MessageListContent.Text>(open().page(limit = 1).items.single().content).text
        assertEquals("original", missing.quotedMessageId)
        assertNull(missing.quote)
    }

    @Test
    fun quotedPollsShowTheirQuestionInTheTimelineAndInMessageDetails() = runTest(dispatcher) {
        val poll = MessageEntityContent.Poll("Lunch?", listOf(PollOptionEntity("a", "Pizza"), PollOptionEntity("b", "Sushi")), false)
        seed(listOf(
            message("poll", poll).copy(date = Instant.fromEpochMilliseconds(0)),
            message("reply", MessageEntityContent.Text("reply", quotedMessageId = "poll", isQuoteVerified = true)),
        ))
        val expected = MessageContent.QuotedMessageDetails.Poll("Lunch?")
        val reply = open().page().items.first { it.id == "reply" }
        assertEquals(expected, assertIs<MessageListContent.Text>(reply.content).text.quote?.quotedContent)
        val stored = assertIs<MessageEntity.Regular>(db.messageDAO.getMessageById("reply", conversation.id))
        val details = assertIs<MessageContent.Text>(stored.content.toMessageContent(hidden = false, selfUserId = self.id.toModel()))
        assertEquals(expected, details.quotedMessageDetails?.quotedContent)
    }

    @Test
    fun assetsMultipartAndOtherRegularContentHaveTypedDisplayFields() = runTest(dispatcher) {
        val asset = MessageEntityContent.Asset(
            assetSizeInBytes = 300, assetName = "photo.png", assetMimeType = "image/png", assetOtrKey = byteArrayOf(1, 2, 3),
            assetSha256Key = byteArrayOf(4, 5, 6), assetId = "remote-asset", assetDomain = "assets.example", assetToken = "private", assetEncryptionAlgorithm = null,
            assetWidth = 80, assetHeight = 40,
        )
        val attachment = MessageAttachmentEntity(
            "cell-id", "version", true, "video/mp4", "video.mp4", 1_024, "/video", "/preview", 100, 60, 1_500,
            "SAVED_INTERNALLY", "https://private-content", "hash", 0, 2_000, true,
        )
        seed(listOf(
            message("asset", asset),
            message("multipart", MessageEntityContent.Multipart("multipart body", attachments = listOf(attachment))),
            message("unknown", MessageEntityContent.Unknown("future-type", ByteArray(10_000))),
            message("failed", MessageEntityContent.FailedDecryption(ByteArray(10_000), 42, true, other.id, "client")),
            message("restricted", MessageEntityContent.RestrictedAsset("application/pdf", 120, "restricted.pdf")),
            message("location", MessageEntityContent.Location(50f, 10f, "Berlin", 12)),
            message("knock", MessageEntityContent.Knock(false)),
            message("deleted").copy(visibility = MessageEntity.Visibility.DELETED),
        ))
        db.messageDAO.markProteusMessagesAsDecryptionResolved(other.id, "clientId")
        val items = open().page().items.associateBy { it.id }
        val image = assertIs<MessageListContent.Asset>(items.getValue("asset").content).asset
        assertEquals("remote-asset", image.id.value)
        assertEquals("assets.example", image.id.domain)
        assertEquals("photo.png", image.name)
        assertEquals(80, image.width)
        assertTrue(image.hasCompleteRemoteData)
        val multipart = assertIs<MessageListContent.Multipart>(items.getValue("multipart").content)
        assertEquals("multipart body", multipart.text.body)
        assertEquals("cell-id", multipart.attachments.single().id)
        assertEquals("/preview", multipart.attachments.single().previewUrl)
        assertEquals(1_500L, multipart.attachments.single().durationMs)
        assertTrue(multipart.attachments.single().isEditSupported)
        assertEquals(MessageListContent.Unknown("future-type"), items.getValue("unknown").content)
        assertEquals(MessageListContent.FailedDecryption(42, true), items.getValue("failed").content)
        assertEquals(MessageListContent.RestrictedAsset("restricted.pdf", "application/pdf", 120), items.getValue("restricted").content)
        assertEquals(MessageListContent.Location(50f, 10f, "Berlin", 12), items.getValue("location").content)
        assertEquals(MessageListContent.Knock, items.getValue("knock").content)
        assertEquals(MessageListContent.Deleted, items.getValue("deleted").content)
    }

    @Test
    fun statusExpiryAndDeliveryFailuresKeepUserDisplaySemantics() = runTest(dispatcher) {
        val messages = MessageEntity.Status.entries.map { status ->
            message(status.name).copy(
                status = status, editStatus = MessageEntity.EditStatus.Edited(Instant.fromEpochMilliseconds(2_000)),
                expireAfterMs = 10_000, selfDeletionEndDate = Instant.fromEpochMilliseconds(20_000), expectsReadConfirmation = true,
                deliveryStatus = DeliveryStatusEntity.PartialDelivery(listOf(other.id), listOf(self.id)),
            )
        }
        seed(messages)
        messages.forEach {
            db.messageDAO.insertFailedRecipientDelivery(it.id, conversation.id, listOf(other.id), RecipientFailureTypeEntity.NO_CLIENTS_TO_DELIVER)
            db.messageDAO.insertFailedRecipientDelivery(it.id, conversation.id, listOf(self.id), RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED)
        }
        db.userDAO.upsertUsers(listOf(other.copy(name = "", userType = UserTypeEntity.GUEST, expiresAt = Instant.fromEpochMilliseconds(30_000))))
        val items = open().page().items.associateBy { it.id }
        assertEquals(Message.Status.Pending, items.getValue("PENDING").status)
        assertEquals(Message.Status.Sent, items.getValue("SENT").status)
        assertEquals(Message.Status.Delivered, items.getValue("DELIVERED").status)
        assertEquals(Message.Status.Read(0), items.getValue("READ").status)
        assertEquals(Message.Status.Failed, items.getValue("FAILED").status)
        assertEquals(Message.Status.FailedRemotely, items.getValue("FAILED_REMOTELY").status)
        val item = items.getValue("SENT")
        assertTrue(item.sender.isUnavailable) // name-based even though email and handle still exist
        assertEquals(UserTypeInfo.Regular(UserType.GUEST), item.sender.userType)
        assertEquals(Instant.fromEpochMilliseconds(30_000), item.sender.guestExpiresAt)
        assertEquals(Instant.fromEpochMilliseconds(2_000), item.editDate)
        assertEquals(10_000, item.expirationData?.expireAfter?.inWholeMilliseconds?.toInt())
        assertIs<Message.ExpirationData.SelfDeletionStatus.Started>(item.expirationData?.selfDeletionStatus)
        assertTrue(item.expectsReadConfirmation)
        assertSame(item.sender, item.deliveryFailures.noClients.single())
        assertTrue(item.deliveryFailures.failedDelivery.single().isSelf)
    }

    @Test
    fun systemEventsMatchTheExistingTypedContentAndBatchTheirMembers() = runTest(dispatcher) {
        val contents = listOf(
            MessageEntityContent.MemberChange(listOf(self.id, other.id), MessageEntity.MemberChangeType.ADDED),
            MessageEntityContent.MemberChange(listOf(self.id), MessageEntity.MemberChangeType.USER_PROMOTED_TO_ADMIN),
            MessageEntityContent.LegalHold(listOf(other.id), MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS),
            MessageEntityContent.Federation(listOf("one.example", "two.example"), MessageEntity.FederationType.CONNECTION_REMOVED),
            MessageEntityContent.ConversationRenamed("a \"new\" name"),
            MessageEntityContent.ConversationMessageTimerChanged(1_000),
            MessageEntityContent.NewConversationReceiptMode(true),
            MessageEntityContent.ConversationReceiptModeChanged(false),
            MessageEntityContent.ConversationAppsAccessChanged(true),
            MessageEntityContent.ConversationProtocolChanged(ConversationEntity.Protocol.MLS),
            MessageEntityContent.MissedCall,
            MessageEntityContent.CryptoSessionReset,
            MessageEntityContent.HistoryLost,
            MessageEntityContent.HistoryLostProtocolChanged,
            MessageEntityContent.ConversationCreated,
            MessageEntityContent.MLSWrongEpochWarning,
            MessageEntityContent.ConversationDegradedMLS,
            MessageEntityContent.ConversationVerifiedMLS,
            MessageEntityContent.ConversationDegradedProteus,
            MessageEntityContent.ConversationVerifiedProteus,
            MessageEntityContent.ConversationProtocolChangedDuringACall,
            MessageEntityContent.ConversationStartedUnverifiedWarning,
            MessageEntityContent.NewConversationWithCellMessage,
            MessageEntityContent.NewConversationWithCellSelfDeleteDisabledMessage,
            MessageEntityContent.CellEditorAccessMessage,
            MessageEntityContent.CellViewerAccessMessage,
        )
        seed(contents.mapIndexed { index, content ->
            MessageEntity.System(
                id = "system-$index", content = content, conversationId = conversation.id,
                date = Instant.fromEpochMilliseconds(index.toLong()), senderUserId = other.id,
                status = MessageEntity.Status.SENT, expireAfterMs = null, selfDeletionEndDate = null, readCount = 0, senderName = other.name,
            )
        })
        val items = open().page().items.associateBy { it.id }
        contents.forEachIndexed { index, expected ->
            val content = assertIs<MessageListContent.System>(items.getValue("system-$index").content)
            assertEquals(expected.toMessageContent(), content.event)
        }
        val members = assertIs<MessageListContent.System>(items.getValue("system-0").content).members
        assertEquals(listOf(self.id.toModel(), other.id.toModel()), members.map { it.id })
        assertSame(items.getValue("system-0").sender, members.last())
    }

    @Test
    fun pagingRestoresTheVisibleMessageAfterTimestampChangesAndUsesUndefinedPositions() = runTest(dispatcher) {
        seed((0..79).map { index -> message(index.toString().padStart(3, '0')).copy(date = Instant.fromEpochMilliseconds(index.toLong())) })
        val source = source()
        val first = assertIs<PagingSource.LoadResult.Page<MessageTimelineCursor, MessageListItem>>(
            source.load(PagingSource.LoadParams.Refresh(null, 20, false))
        )
        val second = assertIs<PagingSource.LoadResult.Page<MessageTimelineCursor, MessageListItem>>(
            source.load(PagingSource.LoadParams.Append(requireNotNull(first.nextKey), 20, false))
        )
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, first.itemsBefore)
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, first.itemsAfter)
        assertFalse(source.jumpingSupported)
        val state = PagingState(listOf(first, second), 25, PagingConfig(20, enablePlaceholders = false), 0)
        val refresh = assertNotNull(source.getRefreshKey(state))
        assertEquals(second.data[5].id, refresh.messageId)
        db.messageDAO.promoteMessageToSentUpdatingServerTime(conversation.id, refresh.messageId, Instant.fromEpochMilliseconds(10_000), 0)
        assertTrue(source.invalid)
        val restored = assertIs<PagingSource.LoadResult.Page<MessageTimelineCursor, MessageListItem>>(
            source().load(PagingSource.LoadParams.Refresh(refresh, 60, false))
        )
        assertEquals(refresh.messageId, restored.data.first().id)
        assertEquals(60, restored.data.size)
        assertNull(restored.prevKey)
        assertNotNull(restored.nextKey)
    }

    @Test
    fun publicResultsAndSessionLifetimeAreConcrete() = runTest(dispatcher) {
        seed(listOf(message("message")))
        val timeline = open()
        assertEquals(MessageTimelineResult.InvalidLimit, timeline.load(MessageTimelineRequest.Latest, 0))
        assertEquals(MessageTimelineResult.AnchorNotFound, timeline.load(MessageTimelineRequest.AroundMessage("missing")))
        val page = timeline.page()
        assertEquals(1, page.items.size)
        assertNull(page.olderCursor)
        assertNull(page.newerCursor)
        timeline.close()
        assertTrue(timeline.invalidated.value)
        assertEquals(MessageTimelineResult.Invalidated, timeline.load(MessageTimelineRequest.Latest))
    }
}
