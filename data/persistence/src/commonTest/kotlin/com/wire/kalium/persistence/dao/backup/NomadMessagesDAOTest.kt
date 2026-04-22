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

package com.wire.kalium.persistence.dao.backup

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class NomadMessagesDAOTest : BaseDatabaseTest() {

    private lateinit var nomadMessagesDAO: NomadMessagesDAO
    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private val selfUser = newUserEntity("self-user")
    private val otherUser = newUserEntity("other-user")
    private val conversation = newConversationEntity("nomad-conversation")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUser.id)
        val db = createDatabase(selfUser.id, encryptedDBSecret, true)
        nomadMessagesDAO = db.nomadMessagesDAO
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenNomadRemoteMessageAfterLastRead_whenRestoringMessages_thenShouldCreateUnreadIndicator() = runTest {
        insertInitialData()
        val lastRead = Instant.parse("2026-04-17T10:00:00Z")
        conversationDAO.updateConversationReadDate(conversation.id, lastRead)

        nomadMessagesDAO.storeMessages(
            messages = listOf(
                nomadTextMessage(
                    id = "remote-message",
                    senderUserId = otherUser.id,
                    date = lastRead + 1.seconds
                )
            ),
            batchSize = 10
        )

        val unreadEvents = unreadEventsForConversation()
        assertEquals(1, unreadEvents?.get(UnreadEventTypeEntity.MESSAGE))
    }

    @Test
    fun givenNomadMessageMentioningSelf_whenRestoringMessages_thenShouldCreateMentionUnreadIndicator() = runTest {
        insertInitialData()
        val lastRead = Instant.parse("2026-04-17T10:00:00Z")
        conversationDAO.updateConversationReadDate(conversation.id, lastRead)

        nomadMessagesDAO.storeMessages(
            messages = listOf(
                nomadTextMessage(
                    id = "mention-message",
                    senderUserId = otherUser.id,
                    date = lastRead + 1.seconds,
                    mentions = listOf(MessageEntity.Mention(start = 0, length = 5, userId = selfUser.id))
                )
            ),
            batchSize = 10
        )

        val unreadEvents = unreadEventsForConversation()
        assertEquals(1, unreadEvents?.get(UnreadEventTypeEntity.MENTION))
    }

    @Test
    fun givenNomadMessageReplyingToSelf_whenRestoringMessages_thenShouldCreateReplyUnreadIndicator() = runTest {
        insertInitialData()
        val lastRead = Instant.parse("2026-04-17T10:00:00Z")
        conversationDAO.updateConversationReadDate(conversation.id, lastRead)

        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "self-message",
                conversationId = conversation.id,
                senderUserId = selfUser.id,
                content = MessageEntityContent.Text("Question"),
                date = lastRead - 1.seconds,
                status = MessageEntity.Status.SENT
            )
        )

        nomadMessagesDAO.storeMessages(
            messages = listOf(
                nomadTextMessage(
                    id = "reply-message",
                    senderUserId = otherUser.id,
                    date = lastRead + 1.seconds,
                    quotedMessageId = "self-message"
                )
            ),
            batchSize = 10
        )

        val unreadEvents = unreadEventsForConversation()
        assertEquals(1, unreadEvents?.get(UnreadEventTypeEntity.REPLY))
    }

    @Test
    fun givenNomadSelfOrAlreadyReadMessages_whenRestoringMessages_thenShouldNotCreateUnreadIndicator() = runTest {
        insertInitialData()
        val lastRead = Instant.parse("2026-04-17T10:00:00Z")
        conversationDAO.updateConversationReadDate(conversation.id, lastRead)

        nomadMessagesDAO.storeMessages(
            messages = listOf(
                nomadTextMessage(
                    id = "self-message",
                    senderUserId = selfUser.id,
                    date = lastRead + 1.seconds
                ),
                nomadTextMessage(
                    id = "already-read-message",
                    senderUserId = otherUser.id,
                    date = lastRead - 1.seconds
                )
            ),
            batchSize = 10
        )

        assertNull(unreadEventsForConversation())
    }

    private suspend fun insertInitialData() {
        userDAO.upsertUsers(listOf(selfUser, otherUser))
        conversationDAO.insertConversation(conversation)
    }

    private suspend fun unreadEventsForConversation() =
        messageDAO.observeConversationsUnreadEvents().first().firstOrNull { it.conversationId == conversation.id }?.unreadEvents

    private fun nomadTextMessage(
        id: String,
        senderUserId: QualifiedIDEntity,
        date: Instant,
        quotedMessageId: String? = null,
        mentions: List<MessageEntity.Mention> = emptyList(),
    ) = NomadMessageToInsert(
        id = id,
        conversationId = conversation.id,
        date = date,
        payload = SyncableMessagePayloadEntity.Text(
            creationDate = date,
            senderUserId = senderUserId,
            senderClientId = "client-$id",
            lastEditDate = null,
            text = "message-$id",
            quotedMessageId = quotedMessageId,
            mentions = mentions
        )
    )
}
