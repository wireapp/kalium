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

package com.wire.kalium.persistence.dao

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class UnreadConversationBadgeCountTest : BaseDatabaseTest() {
    private val self = UserIDEntity("badge-self", "wire.test")
    private val sender = UserIDEntity("badge-sender", "wire.test")
    private val date = Instant.parse("2026-01-02T00:00:00Z")
    private lateinit var db: UserDatabaseBuilder

    @BeforeTest
    fun setUp() {
        deleteDatabase(self)
        db = createDatabase(self, encryptedDBSecret, enableWAL = true)
    }

    @Test
    fun countsConversationsOnceAndReactsToMuteReadAndDeleteChanges() = runTest(dispatcher) {
        val first = conversation("first")
        val second = conversation("second").copy(archived = true)
        db.userDAO.upsertUser(newUserEntity(sender).copy(team = null))
        db.conversationDAO.insertConversations(listOf(first, second))
        unread(first, "a")
        unread(first, "b")
        unread(second, "c")
        db.conversationDAO.observeUnreadConversationBadgeCount().test {
            assertEquals(2L, awaitItem())
            db.conversationDAO.updateConversationMutedStatus(first.id, ConversationEntity.MutedStatus.ALL_MUTED, date)
            assertEquals(1L, awaitItem())
            db.conversationDAO.updateConversationMutedStatus(first.id, ConversationEntity.MutedStatus.ALL_ALLOWED, date)
            assertEquals(2L, awaitItem())
            db.conversationDAO.updateConversationReadDate(first.id, date)
            assertEquals(1L, awaitItem())
            db.conversationDAO.setConversationDeletedLocally(second.id, true)
            assertEquals(0L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun mentionsOnlyCountsMentionsRepliesAndMissedCallsButNotMessagesOrKnocks() = runTest(dispatcher) {
        db.userDAO.upsertUser(newUserEntity(sender).copy(team = null))
        val types = UnreadEventTypeEntity.entries
        for (type in types) {
            val conversation = conversation(type.name).copy(
                mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED,
            )
            db.conversationDAO.insertConversation(conversation)
            unread(conversation, type.name)
            db.database.unreadEventsQueries.updateEvent(type, type.name, conversation.id)
        }
        assertEquals(3L, db.conversationDAO.observeUnreadConversationBadgeCount().first())
    }

    @Test
    fun onlyTheActiveUnblockedOneToOneConversationCounts() = runTest(dispatcher) {
        val current = conversation("current").copy(type = ConversationEntity.Type.ONE_ON_ONE)
        val old = conversation("old").copy(type = ConversationEntity.Type.ONE_ON_ONE)
        val user = newUserEntity(sender).copy(team = null, activeOneOnOneConversationId = current.id)
        db.conversationDAO.insertConversations(listOf(current, old))
        db.userDAO.upsertUser(user)
        unread(current, "current")
        unread(old, "old")
        assertEquals(1L, db.conversationDAO.observeUnreadConversationBadgeCount().first())
        db.userDAO.upsertConnectionStatuses(mapOf(sender to ConnectionEntity.State.BLOCKED))
        assertEquals(0L, db.conversationDAO.observeUnreadConversationBadgeCount().first())
    }

    @Test
    fun oneToOneConversationStopsCountingWhenTheUserIsDeleted() = runTest(dispatcher) {
        val conversation = conversation("one-on-one").copy(type = ConversationEntity.Type.ONE_ON_ONE)
        val user = newUserEntity(sender).copy(team = null, activeOneOnOneConversationId = conversation.id)
        val userWithoutOneOnOne = newUserEntity(UserIDEntity("badge-other", "wire.test")).copy(team = null)
        db.conversationDAO.insertConversation(conversation)
        db.userDAO.upsertUsers(listOf(user, userWithoutOneOnOne))
        unread(conversation, "one-on-one")
        assertEquals(1L, db.conversationDAO.observeUnreadConversationBadgeCount().first())
        db.userDAO.markAsDeleted(listOf(sender))
        assertEquals(0L, db.conversationDAO.observeUnreadConversationBadgeCount().first())
    }

    @Test
    fun hardDeletingTheLastUnreadMessageUpdatesTheObservedCount() = runTest(dispatcher) {
        val cleared = conversation("cleared")
        val deletedForMe = conversation("deleted-for-me")
        db.userDAO.upsertUser(newUserEntity(sender).copy(team = null))
        db.conversationDAO.insertConversations(listOf(cleared, deletedForMe))
        unread(cleared, "cleared")
        unread(deletedForMe, "deleted-for-me")
        db.conversationDAO.observeUnreadConversationBadgeCount().test {
            assertEquals(2L, awaitItem())
            db.conversationDAO.clearContent(cleared.id)
            assertEquals(1L, awaitItem())
            db.messageDAO.deleteMessage("deleted-for-me", deletedForMe.id)
            assertEquals(0L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun excludesSelfPendingMeetingAndDeletedConversations() = runTest(dispatcher) {
        db.userDAO.upsertUser(newUserEntity(sender).copy(team = null))
        val conversations = listOf(
            conversation("self").copy(type = ConversationEntity.Type.SELF),
            conversation("pending").copy(type = ConversationEntity.Type.CONNECTION_PENDING),
            conversation("meeting").copy(type = ConversationEntity.Type.MEETING),
            conversation("deleted"),
        )
        db.conversationDAO.insertConversations(conversations)
        conversations.forEach { unread(it, it.id.value) }
        db.conversationDAO.setConversationDeletedLocally(conversations.last().id, true)
        assertEquals(0L, db.conversationDAO.observeUnreadConversationBadgeCount().first())
    }

    private fun conversation(id: String) = newConversationEntity(
        id = ConversationIDEntity(id, "wire.test"),
    ).copy(type = ConversationEntity.Type.GROUP, teamId = null)

    private suspend fun unread(conversation: ConversationEntity, id: String) {
        db.messageDAO.insertOrIgnoreMessage(newRegularMessageEntity(
            id = id, conversationId = conversation.id, senderUserId = sender, date = date,
        ))
    }
}
