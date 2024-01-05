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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
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
            messageDAO.getMessageById(TEST_MESSAGE.id, TEST_MESSAGE.conversationId)
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
        messageDAO.insertOrIgnoreMessage(
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
