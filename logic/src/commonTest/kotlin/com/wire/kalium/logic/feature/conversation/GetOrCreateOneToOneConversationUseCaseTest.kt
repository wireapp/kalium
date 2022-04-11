package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs


class GetOrCreateOneToOneConversationUseCaseTest {

    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())

    private lateinit var getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase

    @BeforeTest
    fun setUp() {
        getOrCreateOneToOneConversationUseCase = GetOrCreateOneToOneConversationUseCase(
            conversationRepository = conversationRepository
        )
    }

    @Test
    fun givenConversationDoesNotExist_whenCallingTheUseCase_ThenDoNotCreateAConversationButReturnExisting() = runTest {
        //given
        given(conversationRepository)
            .coroutine { getOne2OneConversationDetailsByUserId(USER_ID) }
            .then { Either.Right(null) }

        given(conversationRepository)
            .coroutine { createOne2OneConversationWithTeamMate(USER_ID) }
            .then { Either.Right(CONVERSATION_ID) }
        //when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        //then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationRepository)
            .coroutine { createOne2OneConversationWithTeamMate(USER_ID) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationExist_whenCallingTheUseCase_ThenCreateAConversationAndReturn() = runTest {
        //given
        given(conversationRepository)
            .coroutine { getOne2OneConversationDetailsByUserId(USER_ID) }
            .then { Either.Right(USER_ID) }
        //when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        //then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationRepository)
            .coroutine { createOne2OneConversationWithTeamMate(USER_ID) }
            .wasNotInvoked()
    }

    private companion object {
        val USER_ID = UserId(value = "userId", domain = "domainId")
        val CONVERSATION_ID = ConversationId(value = "userId", domain = "domainId")
    }

}
