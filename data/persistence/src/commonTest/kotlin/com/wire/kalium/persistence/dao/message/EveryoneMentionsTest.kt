/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

class EveryoneMentionsTest : BaseMessageTest() {
    private val everyone = MessageEntity.Mention(start = 0, length = 4, userId = null)
    private val message = newRegularMessageEntity(
        id = "everyone",
        conversationId = TEST_CONVERSATION_1.id,
        senderUserId = OTHER_USER.id,
        content = MessageEntityContent.Text("@all hello", mentions = listOf(everyone))
    )

    @Test
    fun groupMentionRoundTripsAndCountsAsAMention() = runTest {
        prepareGroup()
        messageDAO.insertOrIgnoreMessage(message)

        val loaded = assertIs<MessageEntityContent.Text>(messageDAO.getMessageById(message.id, message.conversationId)?.content)
        assertEquals(listOf(everyone), loaded.mentions)
        assertEquals(
            listOf(everyone),
            assertIs<MessageEntityContent.Text>(
                messageDAO.getMessagesByConversationAndVisibility(message.conversationId, 1, 0).first().single().content
            ).mentions
        )
        assertEquals(UnreadEventTypeEntity.MENTION, messageDAO.observeUnreadEvents().first()[message.conversationId]?.single()?.type)
        assertIs<MessagePreviewEntityContent.MentionedSelf>(messageDAO.observeLastMessages().first().single().content)
    }

    @Test
    fun everyoneAndDirectSelfMentionProduceOneNotificationAndOnePreview() = runTest {
        prepareGroup()
        messageDAO.insertOrIgnoreMessage(
            message.copy(content = MessageEntityContent.Text("@all @self", mentions = listOf(everyone, MessageEntity.Mention(5, 5, SELF_USER_ID))))
        )
        assertTrue(messageDAO.needsToBeNotified(message.id, message.conversationId))
        assertEquals(1, messageDAO.getNotificationMessage().size)
        assertEquals(1, messageDAO.observeLastMessages().first().size)
    }

    @Test
    fun everyoneRespectsMentionsOnlyMutedAndAwaySettings() = runTest {
        prepareGroup()
        messageDAO.insertOrIgnoreMessage(message)
        conversationDAO.updateConversationMutedStatus(
            message.conversationId, ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED, Instant.DISTANT_PAST
        )
        userDAO.updateUserAvailabilityStatus(SELF_USER_ID, UserAvailabilityStatusEntity.BUSY)
        assertTrue(messageDAO.needsToBeNotified(message.id, message.conversationId))
        userDAO.updateUserAvailabilityStatus(SELF_USER_ID, UserAvailabilityStatusEntity.AWAY)
        assertFalse(messageDAO.needsToBeNotified(message.id, message.conversationId))
        userDAO.updateUserAvailabilityStatus(SELF_USER_ID, UserAvailabilityStatusEntity.AVAILABLE)
        conversationDAO.updateConversationMutedStatus(
            message.conversationId, ConversationEntity.MutedStatus.ALL_MUTED, Instant.DISTANT_PAST
        )
        assertFalse(messageDAO.needsToBeNotified(message.id, message.conversationId))
        assertTrue(messageDAO.getNotificationMessage().isEmpty())
    }

    @Test
    fun everyoneInOneToOneDoesNotBypassMentionsOnlySettings() = runTest {
        insertInitialData()
        conversationDAO.updateConversationMutedStatus(
            message.conversationId, ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED, Instant.DISTANT_PAST
        )
        messageDAO.insertOrIgnoreMessage(message)
        assertFalse(messageDAO.needsToBeNotified(message.id, message.conversationId))
        assertEquals(UnreadEventTypeEntity.MESSAGE, messageDAO.observeUnreadEvents().first()[message.conversationId]?.single()?.type)
    }

    @Test
    fun editsAddAndRemoveEveryoneFromUnreadClassification() = runTest {
        prepareGroup()
        messageDAO.insertOrIgnoreMessage(message.copy(content = MessageEntityContent.Text("hello")))
        messageDAO.updateTextMessageContent(message.conversationId, message.id, message.content as MessageEntityContent.Text)
        assertEquals(UnreadEventTypeEntity.MENTION, messageDAO.observeUnreadEvents().first()[message.conversationId]?.single()?.type)
        messageDAO.updateTextMessageContent(message.conversationId, message.id, MessageEntityContent.Text("hello again"))
        assertEquals(UnreadEventTypeEntity.MESSAGE, messageDAO.observeUnreadEvents().first()[message.conversationId]?.single()?.type)
        assertTrue(assertIs<MessageEntityContent.Text>(messageDAO.getMessageById(message.id, message.conversationId)?.content).mentions.isEmpty())
    }

    @Test
    fun multipartEveryoneCaptionCreatesOneTextNotificationAndMentionEvent() = runTest {
        prepareGroup()
        messageDAO.insertOrIgnoreMessage(
            message.copy(content = MessageEntityContent.Multipart("@all hello", mentions = listOf(everyone)))
        )
        assertTrue(messageDAO.needsToBeNotified(message.id, message.conversationId))
        assertEquals("@all hello", messageDAO.getNotificationMessage().single().text)
        assertEquals(UnreadEventTypeEntity.MENTION, messageDAO.observeUnreadEvents().first()[message.conversationId]?.single()?.type)
        conversationDAO.updateConversationMutedStatus(
            message.conversationId, ConversationEntity.MutedStatus.ALL_MUTED, Instant.DISTANT_PAST
        )
        assertTrue(messageDAO.getNotificationMessage().isEmpty())
    }

    @Test
    fun multipartWithoutEveryoneOrCaptionRemainsSuppressed() = runTest {
        prepareGroup()
        messageDAO.insertOrIgnoreMessages(
            listOf(
                message.copy(content = MessageEntityContent.Multipart("ordinary caption")),
                message.copy(id = "empty", content = MessageEntityContent.Multipart(null, mentions = listOf(everyone)))
            )
        )
        assertTrue(messageDAO.getNotificationMessage().isEmpty())
    }

    @Test
    fun outgoingMultipartEveryoneDoesNotNotifyItsSender() = runTest {
        prepareGroup()
        messageDAO.insertOrIgnoreMessage(
            message.copy(
                senderUserId = SELF_USER_ID,
                isSelfMessage = true,
                content = MessageEntityContent.Multipart("@all hello", mentions = listOf(everyone))
            )
        )
        assertFalse(messageDAO.needsToBeNotified(message.id, message.conversationId))
        assertTrue(messageDAO.getNotificationMessage().isEmpty())
        assertTrue(messageDAO.observeUnreadEvents().first().isEmpty())
    }

    @Test
    fun multipartEditsReclassifyEveryoneAndRetainTheMultipartMessageType() = runTest {
        prepareGroup()
        messageDAO.insertOrIgnoreMessage(message.copy(content = MessageEntityContent.Multipart("plain caption")))
        messageDAO.updateTextMessageContent(
            editInstant = message.date + 1.seconds,
            conversationId = message.conversationId,
            currentMessageId = message.id,
            newTextContent = MessageEntityContent.Text("@all edited", mentions = listOf(everyone)),
            newMessageId = "edited"
        )
        assertEquals(listOf(everyone), assertIs<MessageEntityContent.Multipart>(
            messageDAO.getMessageById("edited", message.conversationId)?.content
        ).mentions)
        assertEquals(UnreadEventTypeEntity.MENTION, messageDAO.observeUnreadEvents().first()[message.conversationId]?.single()?.type)
        assertEquals("edited", messageDAO.getNotificationMessage().single().id)
        messageDAO.updateTextMessageContent(
            editInstant = message.date + 2.seconds,
            conversationId = message.conversationId,
            currentMessageId = "edited",
            newTextContent = MessageEntityContent.Text("plain again"),
            newMessageId = "edited-again"
        )
        assertEquals(UnreadEventTypeEntity.MESSAGE, messageDAO.observeUnreadEvents().first()[message.conversationId]?.single()?.type)
        assertTrue(messageDAO.getNotificationMessage().isEmpty())
    }

    private suspend fun prepareGroup() {
        insertInitialData()
        conversationDAO.insertConversations(listOf(TEST_CONVERSATION_1.copy(type = ConversationEntity.Type.GROUP)))
    }
}
