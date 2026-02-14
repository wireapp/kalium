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

package com.wire.kalium.persistence.dao.message

import app.cash.paging.PagingConfig
import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResultPage
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserDetailsEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MessageThreadDAOTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var messageThreadDAO: MessageThreadDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private val conversation = newConversationEntity("Thread Test")
    private val senderUser = newUserEntity("thread-user")
    private val senderUserDetails = newUserDetailsEntity("thread-user")
    private val selfUserId = UserIDEntity("thread-self", "thread-domain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        messageDAO = db.messageDAO
        messageThreadDAO = db.messageThreadDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenExistingRootAndReply_whenUpsertingAgain_thenUpsertIsIdempotent() = runTest {
        insertInitialData()
        val root = createMessage("root-1", Instant.parse("2026-01-01T00:00:00Z"))
        val reply = createMessage("reply-1", Instant.parse("2026-01-01T00:00:01Z"))
        messageDAO.insertOrIgnoreMessages(listOf(root, reply))

        messageThreadDAO.upsertThreadRoot(
            conversationId = conversation.id,
            rootMessageId = root.id,
            threadId = THREAD_ID_1,
            createdAt = Instant.parse("2026-01-01T00:00:02Z")
        )
        messageThreadDAO.upsertThreadRoot(
            conversationId = conversation.id,
            rootMessageId = root.id,
            threadId = THREAD_ID_2,
            createdAt = Instant.parse("2026-01-01T00:00:03Z")
        )

        messageThreadDAO.upsertThreadItem(
            conversationId = conversation.id,
            messageId = root.id,
            threadId = THREAD_ID_2,
            isRoot = true,
            creationDate = root.date,
            visibility = root.visibility,
        )
        messageThreadDAO.upsertThreadItem(
            conversationId = conversation.id,
            messageId = reply.id,
            threadId = THREAD_ID_2,
            isRoot = false,
            creationDate = reply.date,
            visibility = reply.visibility,
        )
        messageThreadDAO.upsertThreadItem(
            conversationId = conversation.id,
            messageId = reply.id,
            threadId = THREAD_ID_2,
            isRoot = false,
            creationDate = reply.date,
            visibility = reply.visibility,
        )

        val rootMapping = messageThreadDAO.getThreadByRootMessage(conversation.id, root.id)
        val messageThreadId = messageThreadDAO.getThreadIdByMessageId(conversation.id, reply.id)
        val summaries = messageThreadDAO.observeThreadSummariesForRoots(conversation.id, listOf(root.id)).first()

        assertNotNull(rootMapping)
        assertEquals(THREAD_ID_2, rootMapping.threadId)
        assertEquals(THREAD_ID_2, messageThreadId)
        assertEquals(1, summaries.size)
        assertEquals(1L, summaries.first().visibleReplyCount)
    }

    @Test
    fun givenThreadReply_whenGettingConversationMessages_thenReplyIsExcludedFromMainList() = runTest {
        insertInitialData()
        val root = createMessage("root-main", Instant.parse("2026-01-01T00:00:00Z"))
        val reply = createMessage("reply-main", Instant.parse("2026-01-01T00:00:01Z"))
        val nonThread = createMessage("non-thread-main", Instant.parse("2026-01-01T00:00:02Z"))
        messageDAO.insertOrIgnoreMessages(listOf(root, reply, nonThread))

        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, Instant.parse("2026-01-01T00:00:03Z"))
        messageThreadDAO.upsertThreadItem(conversation.id, root.id, THREAD_ID_1, true, root.date, root.visibility)
        messageThreadDAO.upsertThreadItem(conversation.id, reply.id, THREAD_ID_1, false, reply.date, reply.visibility)

        val mainList = messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversation.id,
            limit = 50,
            offset = 0,
            visibility = listOf(MessageEntity.Visibility.VISIBLE)
        ).first()
        val ids = mainList.map { it.id }

        assertContains(ids, root.id)
        assertContains(ids, nonThread.id)
        assertFalse(ids.contains(reply.id))
    }

    @Test
    fun givenThreadPager_whenLoadingThreadMessages_thenOnlyThreadMessagesAreReturned() = runTest {
        insertInitialData()
        val root = createMessage("root-thread", Instant.parse("2026-01-01T00:00:00Z"))
        val reply = createMessage("reply-thread", Instant.parse("2026-01-01T00:00:01Z"))
        val nonThread = createMessage("non-thread", Instant.parse("2026-01-01T00:00:02Z"))
        val otherThreadMessage = createMessage("other-thread", Instant.parse("2026-01-01T00:00:03Z"))
        messageDAO.insertOrIgnoreMessages(listOf(root, reply, nonThread, otherThreadMessage))

        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, Instant.parse("2026-01-01T00:00:04Z"))
        messageThreadDAO.upsertThreadItem(conversation.id, root.id, THREAD_ID_1, true, root.date, root.visibility)
        messageThreadDAO.upsertThreadItem(conversation.id, reply.id, THREAD_ID_1, false, reply.date, reply.visibility)
        messageThreadDAO.upsertThreadItem(
            conversation.id,
            otherThreadMessage.id,
            THREAD_ID_2,
            false,
            otherThreadMessage.date,
            otherThreadMessage.visibility
        )

        val pager = messageDAO.platformExtensions.getPagerForThread(
            conversationId = conversation.id,
            threadId = THREAD_ID_1,
            visibilities = listOf(MessageEntity.Visibility.VISIBLE),
            pagingConfig = PagingConfig(pageSize = 50),
            startingOffset = 0
        )
        val page = pager.pagingSource.load(PagingSourceLoadParamsRefresh<Int>(null, 50, false))

        assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(page)
        val ids = page.data.map { it.id }
        assertContains(ids, root.id)
        assertContains(ids, reply.id)
        assertFalse(ids.contains(nonThread.id))
        assertFalse(ids.contains(otherThreadMessage.id))
    }

    @Test
    fun givenDeletedReply_whenObservingThreadSummaries_thenReplyCountIsRepliesOnlyAndVisibilityAware() = runTest {
        insertInitialData()
        val root = createMessage("root-summary", Instant.parse("2026-01-01T00:00:00Z"))
        val visibleReply = createMessage("reply-visible", Instant.parse("2026-01-01T00:00:01Z"))
        val deletedReply = createMessage(
            id = "reply-deleted",
            date = Instant.parse("2026-01-01T00:00:02Z"),
            visibility = MessageEntity.Visibility.DELETED
        )
        messageDAO.insertOrIgnoreMessages(listOf(root, visibleReply, deletedReply))

        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, Instant.parse("2026-01-01T00:00:03Z"))
        messageThreadDAO.upsertThreadItem(conversation.id, root.id, THREAD_ID_1, true, root.date, root.visibility)
        messageThreadDAO.upsertThreadItem(
            conversation.id,
            visibleReply.id,
            THREAD_ID_1,
            false,
            visibleReply.date,
            visibleReply.visibility
        )
        messageThreadDAO.upsertThreadItem(
            conversation.id,
            deletedReply.id,
            THREAD_ID_1,
            false,
            deletedReply.date,
            deletedReply.visibility
        )

        val summary = messageThreadDAO.observeThreadSummariesForRoots(conversation.id, listOf(root.id))
            .first()
            .first()

        assertEquals(root.id, summary.rootMessageId)
        assertEquals(THREAD_ID_1, summary.threadId)
        assertEquals(1L, summary.visibleReplyCount)
    }

    private suspend fun insertInitialData() {
        userDAO.upsertUsers(listOf(senderUser))
        conversationDAO.insertConversation(conversation)
    }

    private fun createMessage(
        id: String,
        date: Instant,
        visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE,
        conversationId: ConversationIDEntity = conversation.id,
    ): MessageEntity = newRegularMessageEntity(
        id = id,
        conversationId = conversationId,
        senderUserId = senderUser.id,
        senderName = senderUser.name!!,
        sender = senderUserDetails,
        visibility = visibility,
        date = date,
    )

    private companion object {
        const val THREAD_ID_1 = "45f65280-6024-4452-a32d-53b39fd06c11"
        const val THREAD_ID_2 = "8b98699d-f3e8-4daf-beb5-e01cd95e8cd2"
    }
}
