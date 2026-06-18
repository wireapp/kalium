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
import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserDetailsEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun givenThreadReplyWithoutKnownRoot_whenGettingConversationMessages_thenFakeRootIsShownInMainList() = runTest {
        insertInitialData()
        val beforeUpsert = kotlinx.datetime.Clock.System.now()
        val reply = createMessage("orphan-reply-main", Instant.parse("2026-01-01T00:00:00Z"))
        val nonThread = createMessage("non-thread-orphan-main", Instant.parse("2026-01-01T00:00:01Z"))
        messageDAO.insertOrIgnoreMessages(listOf(reply, nonThread))

        messageThreadDAO.upsertThreadItem(conversation.id, reply.id, THREAD_ID_1, false, reply.date, reply.visibility)
        val afterUpsert = kotlinx.datetime.Clock.System.now()

        val mainList = messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversation.id,
            limit = 50,
            offset = 0,
            visibility = listOf(MessageEntity.Visibility.VISIBLE)
        ).first()
        val ids = mainList.map { it.id }
        val fakeRoot = messageDAO.getMessageById(THREAD_ID_1, conversation.id)
        val rootMapping = messageThreadDAO.getThreadByRootMessage(conversation.id, THREAD_ID_1)
        val summaries = messageThreadDAO.observeThreadSummariesForRoots(conversation.id, listOf(THREAD_ID_1)).first()

        assertContains(ids, THREAD_ID_1)
        assertContains(ids, nonThread.id)
        assertFalse(ids.contains(reply.id))
        assertIs<MessageEntity.Regular>(fakeRoot)
        assertIs<MessageEntityContent.MissingThreadRoot>(fakeRoot.content)
        assertEquals(THREAD_ID_1, rootMapping?.rootMessageId)
        assertEquals(THREAD_ID_1, rootMapping?.threadId)
        assertEquals(1L, rootMapping?.visibleReplyCount)
        val fakeRootCreatedAt = assertNotNull(rootMapping?.createdAt)
        assertEquals(true, fakeRootCreatedAt >= beforeUpsert)
        assertEquals(true, fakeRootCreatedAt <= afterUpsert)
        assertEquals(1, summaries.size)
        assertEquals(1L, summaries.single().visibleReplyCount)
    }

    @Test
    fun givenBackupMissingThreadRoot_whenUpsertingFromBackup_thenFakeRootUsesBackedUpCreationDate() = runTest {
        insertInitialData()
        val fakeRootCreatedAt = Instant.parse("2026-01-01T00:00:00Z")
        val reply = createMessage("backup-orphan-reply-main", Instant.parse("2026-01-02T00:00:00Z"))
        val nonThread = createMessage("backup-non-thread-orphan-main", Instant.parse("2026-01-03T00:00:00Z"))
        messageDAO.insertOrIgnoreMessages(listOf(reply, nonThread))

        messageThreadDAO.upsertMissingThreadRootFromBackup(
            conversationId = conversation.id,
            replyMessageId = reply.id,
            threadId = THREAD_ID_1,
            createdAt = fakeRootCreatedAt,
        )
        messageThreadDAO.upsertThreadItem(conversation.id, reply.id, THREAD_ID_1, false, reply.date, reply.visibility)

        val mainList = messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversation.id,
            limit = 50,
            offset = 0,
            visibility = listOf(MessageEntity.Visibility.VISIBLE)
        ).first()
        val fakeRoot = messageDAO.getMessageById(THREAD_ID_1, conversation.id)
        val rootMapping = messageThreadDAO.getThreadByRootMessage(conversation.id, THREAD_ID_1)

        assertContentEquals(listOf(nonThread.id, THREAD_ID_1), mainList.map { it.id })
        assertIs<MessageEntity.Regular>(fakeRoot)
        assertIs<MessageEntityContent.MissingThreadRoot>(fakeRoot.content)
        assertEquals(fakeRootCreatedAt, fakeRoot.date)
        assertEquals(fakeRootCreatedAt, rootMapping?.createdAt)
        assertEquals(1L, rootMapping?.visibleReplyCount)
    }

    @Test
    fun givenNewThreadRoot_whenReadingFollowState_thenItDefaultsToFollowing() = runTest {
        insertInitialData()
        val root = createMessage("follow-default-root", Instant.parse("2026-01-01T00:00:00Z"))
        messageDAO.insertOrIgnoreMessage(root)

        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, root.date)

        assertEquals(true, messageThreadDAO.getThreadFollowState(conversation.id, THREAD_ID_1))
        assertEquals(true, messageThreadDAO.observeThreadFollowState(conversation.id, THREAD_ID_1).first())
    }

    @Test
    fun givenThreadReplyArrivesBeforeRealRoot_whenRootIsPersisted_thenFakeRootIsReplaced() = runTest {
        insertInitialData()
        val root = createMessage(
            id = THREAD_ID_1,
            date = Instant.parse("2026-01-01T00:00:00Z"),
            content = MessageEntityContent.Text("Real root")
        )
        val reply = createMessage("early-reply-main", Instant.parse("2026-01-01T00:00:01Z"))
        val nonThread = createMessage("non-thread-late-root-main", Instant.parse("2026-01-01T00:00:02Z"))
        messageDAO.insertOrIgnoreMessages(listOf(reply, nonThread))

        messageThreadDAO.upsertThreadItem(conversation.id, reply.id, THREAD_ID_1, false, reply.date, reply.visibility)
        messageDAO.insertOrIgnoreMessages(listOf(root))
        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, root.date)
        messageThreadDAO.upsertThreadItem(conversation.id, root.id, THREAD_ID_1, true, root.date, root.visibility)

        val mainList = messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversation.id,
            limit = 50,
            offset = 0,
            visibility = listOf(MessageEntity.Visibility.VISIBLE)
        ).first()
        val ids = mainList.map { it.id }
        val summary = messageThreadDAO.observeThreadSummariesForRoots(conversation.id, listOf(root.id)).first().single()
        val persistedRoot = messageDAO.getMessageById(root.id, conversation.id)

        assertContains(ids, root.id)
        assertContains(ids, nonThread.id)
        assertFalse(ids.contains(reply.id))
        assertIs<MessageEntity.Regular>(persistedRoot)
        val rootContent = assertIs<MessageEntityContent.Text>(persistedRoot.content)
        assertEquals("Real root", rootContent.messageBody)
        assertEquals(root.id, summary.rootMessageId)
        assertEquals(THREAD_ID_1, summary.threadId)
        assertEquals(1L, summary.visibleReplyCount)
    }

    @Test
    fun givenThreadPager_whenLoadingThreadMessages_thenOnlyThreadMessagesAreReturned() = runTest {
        insertInitialData()
        val root = createMessage("root-thread", Instant.parse("2026-01-01T00:00:00Z"))
        val reply = createMessage("reply-thread", Instant.parse("2026-01-01T00:00:01Z"))
        val threadKnock = createMessage(
            id = "thread-knock",
            date = Instant.parse("2026-01-01T00:00:01.500Z"),
            content = MessageEntityContent.Knock(false)
        )
        val threadMissedCall = newSystemMessageEntity(
            id = "thread-missed-call",
            date = Instant.parse("2026-01-01T00:00:01.750Z"),
            conversationId = conversation.id,
            senderUserId = senderUser.id,
            content = MessageEntityContent.MissedCall
        )
        val nonThread = createMessage("non-thread", Instant.parse("2026-01-01T00:00:02Z"))
        val otherThreadMessage = createMessage("other-thread", Instant.parse("2026-01-01T00:00:03Z"))
        messageDAO.insertOrIgnoreMessages(listOf(root, reply, threadKnock, threadMissedCall, nonThread, otherThreadMessage))

        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, Instant.parse("2026-01-01T00:00:04Z"))
        messageThreadDAO.upsertThreadItem(conversation.id, root.id, THREAD_ID_1, true, root.date, root.visibility)
        messageThreadDAO.upsertThreadItem(conversation.id, reply.id, THREAD_ID_1, false, reply.date, reply.visibility)
        messageThreadDAO.upsertThreadItem(
            conversation.id,
            threadKnock.id,
            THREAD_ID_1,
            false,
            threadKnock.date,
            threadKnock.visibility
        )
        messageThreadDAO.upsertThreadItem(
            conversation.id,
            threadMissedCall.id,
            THREAD_ID_1,
            false,
            threadMissedCall.date,
            threadMissedCall.visibility
        )
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

        assertIs<PagingSourceLoadResultPage<Int, ThreadMessageEntity>>(page)
        val ids = page.data.map { it.message.id }
        assertContains(ids, root.id)
        assertContains(ids, reply.id)
        assertFalse(ids.contains(threadKnock.id))
        assertFalse(ids.contains(threadMissedCall.id))
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

    @Test
    fun givenThreadData_whenGettingBackupRows_thenOnlyVisibleSupportedMessagesAreReturned() = runTest {
        insertInitialData()
        val root = createMessage("backup-root", Instant.parse("2026-01-01T00:00:00Z"))
        val visibleReply = createMessage("backup-reply-visible", Instant.parse("2026-01-01T00:00:01Z"))
        val deletedReply = createMessage(
            id = "backup-reply-deleted",
            date = Instant.parse("2026-01-01T00:00:02Z"),
            visibility = MessageEntity.Visibility.DELETED
        )
        val unsupportedReply = createMessage(
            id = "backup-reply-knock",
            date = Instant.parse("2026-01-01T00:00:03Z"),
            content = MessageEntityContent.Knock(false)
        )
        messageDAO.insertOrIgnoreMessages(listOf(root, visibleReply, deletedReply, unsupportedReply))

        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, root.date)
        messageThreadDAO.upsertThreadItem(conversation.id, root.id, THREAD_ID_1, true, root.date, root.visibility)
        messageThreadDAO.upsertThreadItem(conversation.id, visibleReply.id, THREAD_ID_1, false, visibleReply.date, visibleReply.visibility)
        messageThreadDAO.upsertThreadItem(conversation.id, deletedReply.id, THREAD_ID_1, false, deletedReply.date, deletedReply.visibility)
        messageThreadDAO.upsertThreadItem(
            conversation.id,
            unsupportedReply.id,
            THREAD_ID_1,
            false,
            unsupportedReply.date,
            unsupportedReply.visibility
        )

        val contentTypes = listOf(
            MessageEntity.ContentType.TEXT,
            MessageEntity.ContentType.ASSET,
            MessageEntity.ContentType.LOCATION,
            MessageEntity.ContentType.MULTIPART,
            MessageEntity.ContentType.COMPOSITE,
        )
        val roots = messageThreadDAO.getThreadRootsForBackup(contentTypes, limit = 50, offset = 0)
        val items = messageThreadDAO.getThreadItemsForBackup(contentTypes, limit = 50, offset = 0)

        assertEquals(1L, messageThreadDAO.countThreadRootsForBackup(contentTypes))
        assertEquals(2L, messageThreadDAO.countThreadItemsForBackup(contentTypes))
        assertEquals(root.id, roots.single().rootMessageId)
        assertContentEquals(listOf(root.id, visibleReply.id), items.map { it.messageId })
    }

    @Test
    fun givenThreadsAcrossConversations_whenObservingGlobalThreads_thenThreadsAreSortedByLatestActivity() = runTest {
        insertInitialData()
        val secondConversation = newConversationEntity("Second Thread Test")
        conversationDAO.insertConversation(secondConversation)

        val firstRoot = createMessage(
            id = "global-root-1",
            date = Instant.parse("2026-01-01T00:00:00Z"),
            content = MessageEntityContent.Text("First thread topic")
        )
        val firstReply = createMessage(
            id = "global-reply-1",
            date = Instant.parse("2026-01-01T00:00:01Z"),
        )
        val secondRoot = createMessage(
            id = "global-root-2",
            date = Instant.parse("2026-01-01T00:00:02Z"),
            content = MessageEntityContent.Text("Second thread topic"),
            conversationId = secondConversation.id
        )
        val secondReply = createMessage(
            id = "global-reply-2",
            date = Instant.parse("2026-01-01T00:00:04Z"),
            conversationId = secondConversation.id
        )
        messageDAO.insertOrIgnoreMessages(listOf(firstRoot, firstReply, secondRoot, secondReply))

        messageThreadDAO.upsertThreadRoot(conversation.id, firstRoot.id, THREAD_ID_1, firstRoot.date)
        messageThreadDAO.upsertThreadItem(conversation.id, firstRoot.id, THREAD_ID_1, true, firstRoot.date, firstRoot.visibility)
        messageThreadDAO.upsertThreadItem(conversation.id, firstReply.id, THREAD_ID_1, false, firstReply.date, firstReply.visibility)

        messageThreadDAO.upsertThreadRoot(secondConversation.id, secondRoot.id, THREAD_ID_2, secondRoot.date)
        messageThreadDAO.upsertThreadItem(secondConversation.id, secondRoot.id, THREAD_ID_2, true, secondRoot.date, secondRoot.visibility)
        messageThreadDAO.upsertThreadItem(
            secondConversation.id,
            secondReply.id,
            THREAD_ID_2,
            false,
            secondReply.date,
            secondReply.visibility
        )

        val globalThreads = messageThreadDAO.observeGlobalThreads().first()

        assertEquals(2, globalThreads.size)
        assertEquals(THREAD_ID_2, globalThreads.first().threadId)
        assertEquals(secondConversation.name, globalThreads.first().conversationName)
        assertEquals(1L, globalThreads.first().visibleReplyCount)
        assertIs<MessagePreviewEntityContent.Text>(globalThreads.first().rootMessage.content)
        assertEquals(THREAD_ID_1, globalThreads.last().threadId)
    }

    @Test
    fun givenThreadIsUnfollowed_whenObservingGlobalThreads_thenThreadIsHidden() = runTest {
        insertInitialData()
        val root = createMessage(
            id = "unfollowed-global-root",
            date = Instant.parse("2026-01-01T00:00:00Z"),
            content = MessageEntityContent.Text("Unfollowed thread topic")
        )
        val reply = createMessage("unfollowed-global-reply", Instant.parse("2026-01-01T00:00:01Z"))
        messageDAO.insertOrIgnoreMessages(listOf(root, reply))
        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, root.date)
        messageThreadDAO.upsertThreadItem(conversation.id, root.id, THREAD_ID_1, true, root.date, root.visibility)
        messageThreadDAO.upsertThreadItem(conversation.id, reply.id, THREAD_ID_1, false, reply.date, reply.visibility)

        assertEquals(1, messageThreadDAO.observeGlobalThreads().first().size)

        messageThreadDAO.updateThreadFollowState(conversation.id, THREAD_ID_1, isFollowing = false)

        assertEquals(emptyList(), messageThreadDAO.observeGlobalThreads().first())
    }

    @Test
    fun givenThreadMessages_whenMovingToAnotherConversation_thenMessagesAreFlattenedInTargetConversation() = runTest {
        insertInitialData()
        val targetConversation = newConversationEntity("Promoted Thread")
        conversationDAO.insertConversation(targetConversation)
        val root = createMessage("move-root", Instant.parse("2026-01-01T00:00:00Z"))
        val reply = createMessage("move-reply", Instant.parse("2026-01-01T00:00:01Z"))
        val nonThread = createMessage("move-non-thread", Instant.parse("2026-01-01T00:00:02Z"))
        messageDAO.insertOrIgnoreMessages(listOf(root, reply, nonThread))

        messageThreadDAO.upsertThreadRoot(conversation.id, root.id, THREAD_ID_1, root.date)
        messageThreadDAO.upsertThreadItem(conversation.id, root.id, THREAD_ID_1, true, root.date, root.visibility)
        messageThreadDAO.upsertThreadItem(conversation.id, reply.id, THREAD_ID_1, false, reply.date, reply.visibility)

        assertContentEquals(
            listOf(senderUser.id),
            messageThreadDAO.getThreadParticipantIds(conversation.id, THREAD_ID_1)
        )

        messageThreadDAO.moveThreadMessagesToConversation(
            sourceConversationId = conversation.id,
            threadId = THREAD_ID_1,
            targetConversationId = targetConversation.id,
        )

        val sourceMessageIds = messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversation.id,
            limit = 50,
            offset = 0,
        ).first().map { it.id }
        val targetMessageIds = messageDAO.getMessagesByConversationAndVisibility(
            conversationId = targetConversation.id,
            limit = 50,
            offset = 0,
        ).first().map { it.id }

        assertContentEquals(listOf(nonThread.id), sourceMessageIds)
        assertContains(targetMessageIds, root.id)
        assertContains(targetMessageIds, reply.id)
        assertNull(messageThreadDAO.getThreadIdByMessageId(targetConversation.id, reply.id))
        assertNull(messageThreadDAO.getThreadByRootMessage(targetConversation.id, root.id))
    }

    private suspend fun insertInitialData() {
        userDAO.upsertUsers(listOf(senderUser))
        conversationDAO.insertConversation(conversation)
    }

    private fun createMessage(
        id: String,
        date: Instant,
        visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE,
        content: MessageEntityContent.Regular = MessageEntityContent.Text("Test"),
        conversationId: ConversationIDEntity = conversation.id,
    ): MessageEntity = newRegularMessageEntity(
        id = id,
        conversationId = conversationId,
        senderUserId = senderUser.id,
        senderName = senderUser.name!!,
        sender = senderUserDetails,
        content = content,
        visibility = visibility,
        date = date,
    )

    private companion object {
        const val THREAD_ID_1 = "45f65280-6024-4452-a32d-53b39fd06c11"
        const val THREAD_ID_2 = "8b98699d-f3e8-4daf-beb5-e01cd95e8cd2"
    }
}
