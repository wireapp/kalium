package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageMentionsTest : BaseMessageTest() {

    @Test
    fun givenMentionsAreInserted_whenGettingMessageByConversationIdAndVisibility_thenCorrectMentionsAreReturned() = runTest {
        testTotalMentions {
            messageDAO.getMessagesByConversationAndVisibility(TEST_MESSAGE.conversationId, 1, 0)
                .first()
                .first()
        }
    }

    @Test
    fun givenMentionsAreInserted_whenGettingMessageById_thenCorrectMentionsAreReturned() = runTest {
        testTotalMentions {
            messageDAO.getMessageById(TEST_MESSAGE.id, TEST_MESSAGE.conversationId).first()
        }
    }

    private suspend fun testTotalMentions(
        queryMessageEntity: suspend () -> MessageEntity?
    ) {
        // given
        insertInitialData()

        // when
        val result = queryMessageEntity()

        // then
        val messageResult = (result?.content as MessageEntityContent.Text)
        assertIs<MessageEntity.Regular>(result)
        assertEquals(
            2,
            messageResult.mentions.size
        )
        assertEquals(
            SELF_USER_ID,
            messageResult.mentions.first().userId
        )
        assertEquals(
            OTHER_USER_2.id,
            messageResult.mentions.last().userId
        )
    }

    override suspend fun insertInitialData() {
        super.insertInitialData()
        messageDAO.insertMessage(
            TEST_MESSAGE.copy(
                content = MessageEntityContent.Text(
                    messageBody = "@${SELF_USER.name} @${OTHER_USER_2.name}",
                    mentions = listOf(
                        TEST_MENTION_1,
                        TEST_MENTION_2
                    )
                )
            )
        )
    }

    private companion object {
        val TEST_MENTION_1 = MessageEntity.Mention(
            start = 0,
            length = 9,
            userId = SELF_USER_ID
        )
        val TEST_MENTION_2 = MessageEntity.Mention(
            start = 10,
            length = 11,
            userId = OTHER_USER_2.id
        )
        val TEST_MESSAGE = newRegularMessageEntity(
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = OTHER_USER.id
        )
    }
}
