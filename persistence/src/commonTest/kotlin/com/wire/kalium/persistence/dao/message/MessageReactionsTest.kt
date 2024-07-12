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

import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageReactionsTest : BaseMessageTest() {

    @Test
    fun givenReactionsAreInserted_whenGettingMessageById_thenCorrectReactionCountAreReturned() = runTest {
        testTotalReactionCount(TEST_MESSAGE) {
            messageDAO.getMessageById(TEST_MESSAGE.id, TEST_MESSAGE.conversationId)
        }
    }

    @Test
    fun givenReactionsAreInserted_whenGettingMessageByConversationIdAndVisibility_thenCorrectReactionCountAreReturned() = runTest {
        testTotalReactionCount(TEST_MESSAGE) {
            messageDAO.getMessagesByConversationAndVisibility(TEST_MESSAGE.conversationId, 1, 0)
                .first()
                .first()
        }
    }

    @Test
    fun givenReactionsAreInserted_whenGettingMessageById_thenCorrectSelfUserReactionsAreReturned() = runTest {
        testSelfUserReactions(TEST_MESSAGE) {
            messageDAO.getMessageById(TEST_MESSAGE.id, TEST_MESSAGE.conversationId)
        }
    }

    @Test
    fun givenReactionsAreInserted_whenGettingMessageByConversationIdAndVisibility_thenCorrectSelfUserReactionsAreReturned() = runTest {
        testSelfUserReactions(TEST_MESSAGE) {
            messageDAO.getMessagesByConversationAndVisibility(TEST_MESSAGE.conversationId, 1, 0)
                .first()
                .first()
        }
    }

    private suspend fun testTotalReactionCount(initialMessageEntity: MessageEntity, queryMessageEntity: suspend () -> MessageEntity?) {
        // Given
        insertInitialData()
        val firstEmoji = "🫡"
        val secondEmoji = "🫥"
        val expectedReactionCounts = mapOf(
            firstEmoji to 2,
            secondEmoji to 1
        )
        reactionDAO.insertReaction(
            initialMessageEntity.id,
            initialMessageEntity.conversationId,
            SELF_USER_ID,
            Instant.UNIX_FIRST_DATE,
            firstEmoji
        )
        reactionDAO.insertReaction(
            initialMessageEntity.id,
            initialMessageEntity.conversationId,
            OTHER_USER.id,
            Instant.UNIX_FIRST_DATE,
            firstEmoji
        )
        reactionDAO.insertReaction(
            initialMessageEntity.id,
            initialMessageEntity.conversationId,
            SELF_USER_ID,
            Instant.UNIX_FIRST_DATE,
            secondEmoji
        )

        // When
        val result = queryMessageEntity()

        // Then
        assertIs<MessageEntity.Regular>(result)
        val reactionCount = result.reactions.totalReactions
        assertEquals(expectedReactionCounts.entries, reactionCount.entries)
    }

    private suspend fun testSelfUserReactions(initialMessageEntity: MessageEntity, queryMessageEntity: suspend () -> MessageEntity?) {
        // Given
        insertInitialData()
        val firstEmoji = "🫡"
        val secondEmoji = "🫥"
        val expectedReactionCounts = setOf(firstEmoji, secondEmoji)
        reactionDAO.insertReaction(
            initialMessageEntity.id,
            initialMessageEntity.conversationId,
            SELF_USER_ID,
            Instant.DISTANT_PAST,
            firstEmoji
        )
        reactionDAO.insertReaction(
            initialMessageEntity.id,
            initialMessageEntity.conversationId,
            OTHER_USER.id,
            Instant.DISTANT_PAST,
            firstEmoji
        )
        reactionDAO.insertReaction(
            initialMessageEntity.id,
            initialMessageEntity.conversationId,
            SELF_USER_ID,
            Instant.DISTANT_PAST,
            secondEmoji
        )
        reactionDAO.insertReaction(
            initialMessageEntity.id,
            initialMessageEntity.conversationId,
            OTHER_USER.id,
            Instant.DISTANT_PAST,
            "😡"
        )

        // When
        val result = queryMessageEntity()

        // Then
        assertIs<MessageEntity.Regular>(result)
        val reactionCount = result.reactions.selfUserReactions
        assertEquals(expectedReactionCounts, reactionCount)
    }

    protected override suspend fun insertInitialData() {
        super.insertInitialData()
        messageDAO.insertOrIgnoreMessages(
            listOf(
                TEST_MESSAGE,
                TEST_MESSAGE_2
            )
        )
    }

    private companion object {
        val TEST_MESSAGE = newRegularMessageEntity(
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = OTHER_USER.id
        )
        val TEST_MESSAGE_2 = newRegularMessageEntity(
            conversationId = TEST_CONVERSATION_2.id,
            senderUserId = OTHER_USER.id
        )
    }
}
