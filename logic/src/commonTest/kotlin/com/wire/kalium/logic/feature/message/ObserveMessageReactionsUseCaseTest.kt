package com.wire.kalium.logic.feature.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
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
            userId = USER_ID,
            name = "User Name",
            handle = "userhandle",
            isSelfUser = true,
            previewAssetId = null,
            userType = UserType.INTERNAL,
            deleted = false,
            connectionStatus = ConnectionState.ACCEPTED,
            userAvailabilityStatus = UserAvailabilityStatus.NONE
        )
    }
}
