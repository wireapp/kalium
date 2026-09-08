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
package com.wire.kalium.benchmarks.fixtures

import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.datetime.Instant

internal class MessageFixture(val messageCount: Int = 20_000) {
    val conversation = BenchmarkEntities.conversation.copy(
        lastModifiedDate = Instant.fromEpochMilliseconds(messageCount - 1L),
        lastReadDate = Instant.fromEpochMilliseconds(messageCount - 1L),
    )
    val batch = BenchmarkEntities.messages(5_000, idOffset = messageCount)
    val incoming = batch[1].copy(senderUserId = BenchmarkEntities.userTwo.id)

    suspend fun seed(db: UserDatabaseBuilder) {
        db.userDAO.upsertUsers(BenchmarkEntities.users)
        db.conversationDAO.insertConversations(listOf(conversation))
        db.memberDAO.insertMembersWithQualifiedId(
            BenchmarkEntities.users.map { MemberEntity(it.id, MemberEntity.Role.Member) }, conversation.id
        )
        db.messageDAO.insertOrIgnoreMessages(BenchmarkEntities.messages(messageCount), withUnreadEvents = false)
        check(db.messageDAO.countMessagesForBackup(MessageEntity.ContentType.entries) == messageCount.toLong())
    }

    suspend fun removeBatch(db: UserDatabaseBuilder) {
        check(db.messageDAO.countMessagesForBackup(MessageEntity.ContentType.entries) == messageCount.toLong() + batch.size)
        batch.forEach { db.messageDAO.deleteMessage(it.id, it.conversationId) }
        restoreDates(db)
        check(db.messageDAO.countMessagesForBackup(MessageEntity.ContentType.entries) == messageCount.toLong())
    }

    suspend fun restoreDates(db: UserDatabaseBuilder) {
        db.conversationDAO.updateConversationModifiedDate(conversation.id, conversation.lastModifiedDate)
        db.conversationDAO.updateReadDateAndGetHasUnreadEvents(conversation.id, conversation.lastReadDate)
    }

    fun expectedPageIds(offset: Int, size: Int): List<String> =
        (messageCount - 1 - offset downTo messageCount - offset - size).map(Int::toString)
}
