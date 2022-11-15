package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageReactionsTest : BaseMessageTest() {

    @Test
    fun givenReactionsAreInserted_whenGettingMessageById_thenCorrectReactionCountAreReturned() = runTest {
        testTotalReactionCount(TEST_MESSAGE) {
            messageDAO.getMessageById(TEST_MESSAGE.id, TEST_MESSAGE.conversationId).first()
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
            messageDAO.getMessageById(TEST_MESSAGE.id, TEST_MESSAGE.conversationId).first()
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
        reactionDAO.insertReaction(initialMessageEntity.id, initialMessageEntity.conversationId, SELF_USER_ID, "date", firstEmoji)
        reactionDAO.insertReaction(initialMessageEntity.id, initialMessageEntity.conversationId, OTHER_USER.id, "date", firstEmoji)
        reactionDAO.insertReaction(initialMessageEntity.id, initialMessageEntity.conversationId, SELF_USER_ID, "date", secondEmoji)

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
        reactionDAO.insertReaction(initialMessageEntity.id, initialMessageEntity.conversationId, SELF_USER_ID, "date", firstEmoji)
        reactionDAO.insertReaction(initialMessageEntity.id, initialMessageEntity.conversationId, OTHER_USER.id, "date", firstEmoji)
        reactionDAO.insertReaction(initialMessageEntity.id, initialMessageEntity.conversationId, SELF_USER_ID, "date", secondEmoji)
        reactionDAO.insertReaction(initialMessageEntity.id, initialMessageEntity.conversationId, OTHER_USER.id, "date", "😡")

        // When
        val result = queryMessageEntity()

        // Then
        assertIs<MessageEntity.Regular>(result)
        val reactionCount = result.reactions.selfUserReactions
        assertEquals(expectedReactionCounts, reactionCount)
    }


    protected override suspend fun insertInitialData() {
        super.insertInitialData()
        messageDAO.insertMessages(
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
