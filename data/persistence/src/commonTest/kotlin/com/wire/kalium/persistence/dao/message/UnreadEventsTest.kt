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
package com.wire.kalium.persistence.dao.message

import app.cash.turbine.test
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UnreadEventsTest : BaseMessageTest() {

    @Test
    fun givenUnreadRegularMessagesFromOthers_thenShouldCount() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate)

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text("Hi"),
                date = readDate + 1.seconds
            ),
            newRegularMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text("Yo"),
                date = readDate + 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeConversationsUnreadEvents().first()
            .first { it.conversationId == convId }.unreadEvents
        assertNotNull(result)
        assertEquals(unreadMessages.size, result[UnreadEventTypeEntity.MESSAGE])
    }

    @Test
    fun givenUnreadKnockMessagesFromOthers_thenShouldCount() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate)

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Knock(true),
                date = readDate + 1.seconds
            ),
            newRegularMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Knock(false),
                date = readDate + 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeConversationsUnreadEvents().first()
            .first { it.conversationId == convId }.unreadEvents
        assertNotNull(result)
        assertEquals(unreadMessages.size, result[UnreadEventTypeEntity.KNOCK])
    }

    @Test
    fun givenMissedCallsFromOthers_thenShouldCount() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate)

        val unreadMessages = listOf(
            newSystemMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.MissedCall,
                date = readDate + 1.seconds
            ),
            newSystemMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.MissedCall,
                date = readDate + 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeConversationsUnreadEvents().first()
            .first { it.conversationId == convId }.unreadEvents
        assertNotNull(result)
        assertEquals(unreadMessages.size, result[UnreadEventTypeEntity.MISSED_CALL])
    }

    @Test
    fun givenUnreadSelfMentionsFromOthers_thenShouldCount() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate)

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text(
                    "@John",
                    mentions = listOf(MessageEntity.Mention(0, 5, SELF_USER_ID))
                ),
                date = readDate + 1.seconds
            ),
            newRegularMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text(
                    "@John are you available?",
                    mentions = listOf(MessageEntity.Mention(0, 5, SELF_USER_ID))
                ),
                date = readDate + 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeConversationsUnreadEvents().first()
            .first { it.conversationId == convId }.unreadEvents
        assertNotNull(result)
        assertEquals(unreadMessages.size, result[UnreadEventTypeEntity.MENTION])
    }

    @Test
    fun givenUnreadSelfRepliesFromOthers_thenShouldCount() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate)
        val messageQuotedId = "1"

        val messageToQuote = newRegularMessageEntity(
            id = messageQuotedId,
            conversationId = convId,
            senderUserId = SELF_USER_ID,
            content = MessageEntityContent.Text("Any ideas?"),
            date = readDate + 1.seconds
        )

        messageDAO.insertOrIgnoreMessage(messageToQuote)

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text(
                    "I have some suggestion...",
                    quotedMessageId = messageQuotedId
                ),
                date = readDate + 1.seconds
            ),
            newRegularMessageEntity(
                id = "3",
                conversationId = convId,
                senderUserId = OTHER_USER_2.id,
                content = MessageEntityContent.Text(
                    "Same here...",
                    quotedMessageId = messageQuotedId
                ),
                date = readDate + 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeConversationsUnreadEvents().first()
            .first { it.conversationId == convId }.unreadEvents
        assertNotNull(result)
        assertEquals(unreadMessages.size, result[UnreadEventTypeEntity.REPLY])
    }

    @Test
    fun givenAllReadEvents_thenShouldEmitEmptyMap() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text("Hi"),
                date = readDate - 1.seconds
            ),
            newRegularMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Knock(true),
                date = readDate - 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        conversationDAO.updateConversationReadDate(convId, readDate)

        val result = messageDAO.observeConversationsUnreadEvents().first()
            .firstOrNull { it.conversationId == convId }

        assertNull(result)
    }

    @Test
    fun givenUnreadNotMissedCallSystemMessages_thenShouldEmitEmptyMap() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate)

        val unreadMessages = listOf(
            newSystemMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.ConversationRenamed("Hehe"),
                date = readDate + 1.seconds
            ),
            newSystemMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.TeamMemberRemoved("Hehe"),
                date = readDate + 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeConversationsUnreadEvents().first()
            .firstOrNull { it.conversationId == convId }
        assertNull(result)

    }

    @Test
    fun givenNewUnreadEventIsInserted_thenShouldEmitUpdate() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate)

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text("Hi"),
                date = readDate + 1.seconds
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        messageDAO.observeConversationsUnreadEvents().test {

            awaitItem().first { it.conversationId == convId }.also {
                assertNotNull(it)
                assertEquals(unreadMessages.size, it.unreadEvents.values.sum())
            }

            messageDAO.insertOrIgnoreMessage(
                newRegularMessageEntity(
                    id = "2",
                    conversationId = convId,
                    senderUserId = OTHER_USER.id,
                    content = MessageEntityContent.Knock(true),
                    date = readDate + 1.minutes
                )
            )

            awaitItem().first { it.conversationId == convId }.also {
                assertNotNull(it)
                assertEquals(unreadMessages.size + 1, it.unreadEvents.values.sum())
            }
        }
    }
}
