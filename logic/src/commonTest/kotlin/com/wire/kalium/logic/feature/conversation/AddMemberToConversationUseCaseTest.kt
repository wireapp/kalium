package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlin.test.Test

class AddMemberToConversationUseCaseTest {

    @Test
    fun givenMemberAndConversation_WhenAddMemberIsSuccessful_ThenMemberIsAddedToDB() {
    }

    @Test
    fun givenMemberAndConversation_WhenAddMemberIsFailed_ThenMemberNotAddedToDB() {
    }

    private class Arrangement {
        @Mock
        private val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val idMapper = mock(classOf<IdMapper>())

        private val addMemberUseCase = AddMemberToConversationUseCaseImpl(
            conversationRepository,
            mlsConversationRepository,
            idMapper
        )

        fun withCreateGroupConversationReturning(conversation: Conversation) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::createGroupConversation)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(conversation))
        }

        fun arrange() = this to addMemberUseCase
    }

}
