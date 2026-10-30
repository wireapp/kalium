/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ManualUnreadTest : BaseMessageTest() {
    private val conversationId = TEST_CONVERSATION_1.id
    private val firstDate = Instant.parse("2026-01-01T00:00:00Z")
    private val canonicalDate = firstDate + 10.seconds
    private val operationDate = firstDate + 20.seconds

    @Test
    fun olderIncomingReadsNeverMoveTheCanonicalDateBackwards() = runTest {
        insertReadConversation()

        conversationDAO.updateReadDatesAndGetHasUnreadEvents(mapOf(conversationId to firstDate))

        assertEquals(canonicalDate, conversationDAO.getConversationLastReadDate(conversationId))
    }

    @Test
    fun manualUnreadRestoresIncomingMessagesAcrossOwnMessagesAndKeepsEventTypes() = runTest {
        insertReadConversation()
        messageDAO.insertOrIgnoreMessages(
            listOf(
                message("mention", firstDate, MessageEntityContent.Text(
                    "@self", mentions = listOf(MessageEntity.Mention(0, 5, SELF_USER_ID)),
                )),
                message("own", firstDate + 1.seconds).copy(senderUserId = SELF_USER_ID, isSelfMessage = true),
                message("knock", firstDate + 2.seconds, MessageEntityContent.Knock(true)),
            )
        )
        assertEquals(0L, messageDAO.getConversationUnreadEventsCount(conversationId))

        conversationDAO.markConversationAsUnread(conversationId, firstDate, operationDate)

        assertEquals(canonicalDate, conversationDAO.getConversationLastReadDate(conversationId))
        val conversation = assertNotNull(conversationDAO.getConversationById(conversationId))
        assertEquals(firstDate, conversation.manualUnreadDate)
        assertEquals(operationDate, conversation.manualUnreadUpdatedAt)
        val events = messageDAO.observeConversationsUnreadEvents().first().single().unreadEvents
        assertEquals(mapOf(UnreadEventTypeEntity.MENTION to 1, UnreadEventTypeEntity.KNOCK to 1), events)

        // A queued old read cannot delete the manually restored rows.
        conversationDAO.updateReadDatesAndGetHasUnreadEvents(mapOf(conversationId to canonicalDate))
        assertEquals(2L, messageDAO.getConversationUnreadEventsCount(conversationId))
    }

    @Test
    fun staleReadsCannotClearTheMarkerButRereadingAtTheSameCanonicalDateCan() = runTest {
        insertReadConversation()
        messageDAO.insertOrIgnoreMessage(message("selected", firstDate))
        conversationDAO.markConversationAsUnread(conversationId, firstDate, operationDate)

        conversationDAO.clearManualUnreadMarker(conversationId, canonicalDate, operationDate - 1.seconds)
        assertEquals(firstDate, conversationDAO.getConversationById(conversationId)?.manualUnreadDate)
        assertEquals(1L, messageDAO.getConversationUnreadEventsCount(conversationId))

        conversationDAO.clearManualUnreadMarker(conversationId, canonicalDate, operationDate + 1.seconds)
        assertNull(conversationDAO.getConversationById(conversationId)?.manualUnreadDate)
        assertEquals(canonicalDate, conversationDAO.getConversationLastReadDate(conversationId))
        assertEquals(0L, messageDAO.getConversationUnreadEventsCount(conversationId))

        // A delayed duplicate mark must not recreate unread state after a later read.
        conversationDAO.markConversationAsUnread(conversationId, firstDate, operationDate)
        assertNull(conversationDAO.getConversationById(conversationId)?.manualUnreadDate)
    }

    @Test
    fun manualUnreadSurvivesAnOwnOnlyRangeAndIncludesHistoryFetchedLater() = runTest {
        insertReadConversation()
        messageDAO.insertOrIgnoreMessage(
            message("own", firstDate).copy(senderUserId = SELF_USER_ID, isSelfMessage = true)
        )
        conversationDAO.markConversationAsUnread(conversationId, firstDate, operationDate)

        assertTrue(conversationDAO.updateReadDateAndGetHasUnreadEvents(conversationId, canonicalDate))
        assertEquals(firstDate, conversationDAO.getConversationById(conversationId)?.manualUnreadDate)

        messageDAO.insertOrIgnoreMessage(message("fetched-later", firstDate + 1.seconds))
        assertEquals(1L, messageDAO.getConversationUnreadEventsCount(conversationId))
    }

    private suspend fun insertReadConversation() {
        insertInitialData()
        conversationDAO.updateConversationReadDate(conversationId, canonicalDate)
    }

    private fun message(
        id: String,
        date: Instant,
        content: MessageEntityContent.Regular = MessageEntityContent.Text("text"),
    ) = newRegularMessageEntity(
        id = id,
        content = content,
        conversationId = conversationId,
        senderUserId = OTHER_USER.id,
        status = MessageEntity.Status.SENT,
        date = date,
    )
}
