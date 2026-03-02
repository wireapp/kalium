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

package com.wire.kalium.nomaddevice.dao

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class NomadMessagesDAOTest {

    @Test
    fun givenMessages_whenStoring_thenTheyAreInsertedInConfiguredBatches() = runTest {
        val insertedBatches = mutableListOf<List<MessageEntity>>()
        var insertedUsers = emptyList<UserEntity>()
        var insertedConversations = emptyList<ConversationEntity>()
        val dao = NomadMessagesDAOImpl(
            upsertUsers = { users -> insertedUsers = users },
            upsertConversations = { conversations -> insertedConversations = conversations },
            insertMessages = { batch -> insertedBatches += batch },
        )

        val result = dao.storeMessages(
            selfUserId = SELF_USER_ID,
            messages = listOf(
                message("m1", "c1", "u1", 1_000),
                message("m2", "c1", "u2", 1_100),
                message("m3", "c2", "u3", 1_200),
                message("m4", "c2", "u1", 1_300),
                message("m5", "c3", "u2", 1_400),
            ),
            batchSize = 2
        )

        assertEquals(5, result.storedMessages)
        assertEquals(3, result.batches)
        assertEquals(listOf(2, 2, 1), insertedBatches.map { it.size })
        assertEquals(3, insertedUsers.size)
        assertEquals(3, insertedConversations.size)
    }

    @Test
    fun givenZeroBatchSize_whenStoring_thenMinimumBatchSizeIsUsed() = runTest {
        val insertedBatches = mutableListOf<List<MessageEntity>>()
        val dao = NomadMessagesDAOImpl(
            upsertUsers = {},
            upsertConversations = {},
            insertMessages = { batch -> insertedBatches += batch },
        )

        val result = dao.storeMessages(
            selfUserId = SELF_USER_ID,
            messages = listOf(
                message("m1", "c1", "u1", 1_000),
                message("m2", "c1", "u2", 1_100),
            ),
            batchSize = 0
        )

        assertEquals(2, result.storedMessages)
        assertEquals(2, result.batches)
        assertEquals(listOf(1, 1), insertedBatches.map { it.size })
    }

    private fun message(
        id: String,
        conversationId: String,
        senderId: String,
        timestampMs: Long,
    ) = MessageEntity.Regular(
        id = id,
        conversationId = QualifiedIDEntity(conversationId, "wire.test"),
        date = Instant.fromEpochMilliseconds(timestampMs),
        senderUserId = QualifiedIDEntity(senderId, "wire.test"),
        status = MessageEntity.Status.SENT,
        visibility = MessageEntity.Visibility.VISIBLE,
        content = MessageEntityContent.Text(messageBody = "text-$id"),
        isSelfMessage = false,
        readCount = 0,
        expireAfterMs = null,
        selfDeletionEndDate = null,
        sender = null,
        senderName = null,
        senderClientId = "sender-client",
        editStatus = MessageEntity.EditStatus.NotEdited,
    )

    private companion object {
        val SELF_USER_ID = UserId("self-user", "wire.test")
    }
}
