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

package com.wire.kalium.logic.data.reaction

import app.cash.turbine.test
import com.wire.kalium.logic.data.message.reaction.ReactionRepositoryImpl
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactionRepositoryTest {

    private val userDatabase = TestUserDatabase(TestUser.ENTITY_ID)
    private val reactionsDao = userDatabase.builder.reactionDAO
    private val conversationDao = userDatabase.builder.conversationDAO
    private val userDao = userDatabase.builder.userDAO
    private val messageDao = userDatabase.builder.messageDAO

    private val reactionRepository = ReactionRepositoryImpl(SELF_USER_ID, reactionsDao)

    @AfterTest
    fun tearDown() {
        userDatabase.delete()
    }

    @Test
    fun givenSelfUserReactionWasPersisted_whenGettingSelfUserReactions_thenShouldReturnPreviouslyStored() = runTest {
        insertInitialData()
        val emoji = "🫡"
        reactionRepository.persistReaction(TEST_MESSAGE_ID, TEST_CONVERSATION_ID, SELF_USER_ID, Instant.DISTANT_PAST, emoji)

        val result = reactionRepository.getSelfUserReactionsForMessage(TEST_MESSAGE_ID, TEST_CONVERSATION_ID)

        result.shouldSucceed {
            assertTrue(it.size == 1)
            assertEquals(emoji, it.first())
        }
    }

    @IgnoreIOS // TODO investigate why test is flaky
    @Test
    fun givenSelfUserReactionWasPersisted_whenObservingMessageReactions_thenShouldReturnReactionsPreviouslyStored() = runTest {
        insertInitialData()

        reactionRepository.persistReaction(TEST_MESSAGE_ID, TEST_CONVERSATION_ID, SELF_USER_ID, Instant.DISTANT_PAST, "🤯")
        reactionRepository.persistReaction(TEST_MESSAGE_ID, TEST_CONVERSATION_ID, SELF_USER_ID, Instant.DISTANT_PAST, "❤️")

            reactionRepository.observeMessageReactions(
                messageId = TEST_MESSAGE_ID,
                conversationId = TEST_CONVERSATION_ID
            ).test {
                val result = awaitItem()
                assertTrue(result.size == 2)
            }
    }

    suspend fun insertInitialData() {
        userDao.upsertUser(TEST_SELF_USER_ENTITY)
        conversationDao.insertConversation(TEST_CONVERSATION_ENTITY)
        messageDao.insertOrIgnoreMessage(TEST_MESSAGE_ENTITY)
    }

    private companion object {
        private val SELF_USER_ID = TestUser.USER_ID
        private val TEST_SELF_USER_ENTITY = TestUser.ENTITY.copy(
            id = QualifiedIDEntity(
                SELF_USER_ID.value,
                SELF_USER_ID.domain
            )
        )
        private val TEST_CONVERSATION_ID = TestConversation.ID
        private val TEST_CONVERSATION_ENTITY =
            TestConversation.ENTITY.copy(
                id = QualifiedIDEntity(
                    TEST_CONVERSATION_ID.value,
                    TEST_CONVERSATION_ID.domain
                )
            )
        private val TEST_MESSAGE_ID = TestMessage.TEST_MESSAGE_ID
        private val TEST_MESSAGE_ENTITY = TestMessage.ENTITY.copy(
            id = TEST_MESSAGE_ID,
            senderUserId = TEST_SELF_USER_ENTITY.id,
            conversationId = TEST_CONVERSATION_ENTITY.id
        )
    }
}
