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

package com.wire.kalium.logic.feature.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.message.reaction.MessageReaction
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.stub.ReactionRepositoryStub
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ObserveMessageReactionsUseCaseTest {

    @Test
    fun givenMessageAndConversationId_whenInvokingUseCase_thenShouldCallReactionsRepository() = runTest {
        // given
        var usedConversationId: ConversationId? = null
        var usedMessageId: String? = null

        val reactionRepository = object : ReactionRepositoryStub() {
            override suspend fun observeMessageReactions(conversationId: ConversationId, messageId: String): Flow<List<MessageReaction>> {
                usedConversationId = conversationId
                usedMessageId = messageId

                return flowOf(listOf(MESSAGE_REACTION))
            }
        }

        val observeMessageReactions = ObserveMessageReactionsUseCaseImpl(
            reactionRepository = reactionRepository
        )

        // when
        observeMessageReactions(
            conversationId = CONVERSATION_ID,
            messageId = MESSAGE_ID
        ).test {
            // then
            val result = awaitItem()

            assertEquals(CONVERSATION_ID, usedConversationId)
            assertEquals(MESSAGE_ID, usedMessageId)

            assertContentEquals(listOf(MESSAGE_REACTION), result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val MESSAGE_ID = TestMessage.TEST_MESSAGE_ID
        val CONVERSATION_ID = TestConversation.ID
        val USER_ID = QualifiedID(
            value = "userValue",
            domain = "userDomain"
        )
        val MESSAGE_REACTION = MessageReaction(
            emoji = "🤯",
            isSelfUser = true,
            userSummary = UserSummary(
                userId = USER_ID,
                userName = "User Name",
                userHandle = "userhandle",
                userPreviewAssetId = null,
                userType = UserType.INTERNAL,
                isUserDeleted = false,
                connectionStatus = ConnectionState.ACCEPTED,
                availabilityStatus = UserAvailabilityStatus.NONE
            )
        )
    }
}
