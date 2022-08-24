package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class UpdateConversationMemberRoleUseCaseTest {

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    private lateinit var updateConversationMemberRoleUseCase: UpdateConversationMemberRoleUseCase

    @BeforeTest
    fun setup() {
        updateConversationMemberRoleUseCase = UpdateConversationMemberRoleUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenAConversationIdAndUserId_whenInvokingRoleChangeSucceeds_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val conversationId = TestConversation.ID
        val userId = TestUser.USER_ID
        val newRole = Member.Role.Admin
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationMemberRole)
            .whenInvokedWith(any(), any(), eq(newRole))
            .thenReturn(Either.Right(Unit))

        val result = updateConversationMemberRoleUseCase(conversationId, userId, newRole)
        assertIs<UpdateConversationMemberRoleResult.Success>(result)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationMemberRole)
            .with(any(), any(), eq(newRole))
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenAConversationIdAndUserId_whenInvokingRoleChangeFails_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val conversationId = TestConversation.ID
        val userId = TestUser.USER_ID
        val newRole = Member.Role.Admin
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationMemberRole)
            .whenInvokedWith(any(), any(), eq(newRole))
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))

        val result = updateConversationMemberRoleUseCase(conversationId, userId, newRole)
        assertIs<UpdateConversationMemberRoleResult.Failure>(result)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationMemberRole)
            .with(any(), any(), eq(newRole))
            .wasInvoked(exactly = once)

    }

}
