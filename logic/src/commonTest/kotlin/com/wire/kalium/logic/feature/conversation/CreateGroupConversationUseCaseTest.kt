package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.ConversationRepository
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateGroupConversationUseCaseTest {


    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenRepositoryCreateGroupShouldBeCalled() = runTest {
        val name = "Conv Name"
        val members = setOf(TestUser.USER_ID, TestUser.OTHER.id)
        val createConversationParam = CreateConversationParam.MLS(name = name, teamId = null, userIdSet = members)

        val (arrangement, createGroupConversation) = Arrangement()
            .withUpdateConversationModifiedDateSucceeding()
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .arrange()

        createGroupConversation(createConversationParam)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::createGroupConversation)
            .with(eq(createConversationParam))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenConversationModifiedDateIsUpdated() = runTest {
        val name = "Conv Name"
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val createConversationParam = CreateConversationParam.MLS(name = "conv name", teamId = null, userIdSet = emptySet())

        val (arrangement, createGroupConversation) = Arrangement()
            .withUpdateConversationModifiedDateSucceeding()
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .arrange()

        createGroupConversation(createConversationParam)

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
                .whenInvokedWith(any())
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
