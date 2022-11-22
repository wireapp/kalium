package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class GetOrCreateOneToOneConversationUseCaseTest {

    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())

    @Mock
    private val conversationGroupRepository = mock(classOf<ConversationGroupRepository>())

    private lateinit var getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase

    @BeforeTest
    fun setUp() {
        getOrCreateOneToOneConversationUseCase = GetOrCreateOneToOneConversationUseCase(
            conversationRepository = conversationRepository,
            conversationGroupRepository = conversationGroupRepository
        )
    }

    @Test
    fun givenConversationDoesNotExist_whenCallingTheUseCase_ThenDoNotCreateAConversationButReturnExisting() = runTest {
        // given
        given(conversationRepository)
            .suspendFunction(conversationRepository::observeOneToOneConversationWithOtherUser)
            .whenInvokedWith(anything())
            .thenReturn(flowOf(Either.Right(CONVERSATION)))

        given(conversationRepository)
            .suspendFunction(conversationGroupRepository::createGroupConversation)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(CONVERSATION))
        // when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::createGroupConversation)
            .with(anything(), anything(), anything())
            .wasNotInvoked()

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeOneToOneConversationWithOtherUser)
            .with(anything())
            .wasInvoked()
    }

    @Test
    fun givenConversationExist_whenCallingTheUseCase_ThenCreateAConversationAndReturn() = runTest {
        // given
        given(conversationRepository)
            .coroutine { observeOneToOneConversationWithOtherUser(USER_ID) }
            .then { flowOf(Either.Left(StorageFailure.DataNotFound)) }

        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::createGroupConversation)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(CONVERSATION))
        // when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationGroupRepository)
            .coroutine { createGroupConversation(usersList = MEMBER) }
            .wasInvoked()
    }

    private companion object {
        val USER_ID = UserId(value = "userId", domain = "domainId")
        val MEMBER = listOf(USER_ID)
        val CONVERSATION_ID = ConversationId(value = "userId", domain = "domainId")
        val CONVERSATION = Conversation(
            id = CONVERSATION_ID,
            name = null,
            type = Conversation.Type.ONE_ON_ONE,
            teamId = null,
            ProtocolInfo.Proteus,
            MutedConversationStatus.AllAllowed,
            null,
            null,
            null,
            lastReadDate = "2022-03-30T15:36:00.000Z",
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
            creatorId = null
        )
    }
}
