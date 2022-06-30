package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.wasInTheLastSecond
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
import kotlin.test.Test

class CreateGroupConversationUseCaseTest {


    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenRepositoryCreateGroupShouldBeCalled() = runTest {
        val name = "Conv Name"
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = ConversationOptions(protocol = ConversationOptions.Protocol.MLS)

        val (arrangement, createGroupConversation) = Arrangement()
            .withUpdateConversationModifiedDateSucceeding()
            .withCreateGroupConversationReturning(TestConversation.GROUP)
            .arrange()

        createGroupConversation(name, members, conversationOptions)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::createGroupConversation)
            .with(eq(name), eq(members), eq(conversationOptions))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenConversationModifiedDateIsUpdated() = runTest {
        val name = "Conv Name"
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = ConversationOptions(protocol = ConversationOptions.Protocol.MLS)

        val (arrangement, createGroupConversation) = Arrangement()
            .withUpdateConversationModifiedDateSucceeding()
            .withCreateGroupConversationReturning(TestConversation.GROUP)
            .arrange()

        createGroupConversation(name, members, conversationOptions)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(any(), matching { it.toInstant().wasInTheLastSecond })
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val syncManager = configure(mock(SyncManager::class)) {
            stubsUnitByDefault = true
        }

        private val createGroupConversation = CreateGroupConversationUseCase(
            conversationRepository, syncManager
        )

        fun withCreateGroupConversationReturning(conversation: Conversation) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::createGroupConversation)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(conversation))
        }

        fun withUpdateConversationModifiedDateSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to createGroupConversation
    }

}
